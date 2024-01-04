package org.foo.modules.jahia.strava.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.foo.modules.jahia.strava.client.StravaClient;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.services.content.JCRAutoSplitUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Component(service = EventHandler.class, immediate = true, property = EventConstants.EVENT_TOPIC + "=org/jahia/usersgroups/login/LOGIN")
public class StravaLoginListener implements EventHandler {
    private static final Logger logger = LoggerFactory.getLogger(StravaLoginListener.class);

    private static final String MY_STRAVA_PROFILE_ACTIVITES_FOLDER = "strava-activities";
    private static final String STRAVA_ACTIVITY_DATE = "date";

    @Reference
    private JCRTemplate jcrTemplate;
    @Reference
    private JCRPublicationService jcrPublicationService;
    @Reference
    private StravaClient stravaClient;

    @Override
    public void handleEvent(Event event) {
        if ("org/jahia/usersgroups/login/LOGIN".equals(event.getTopic())) {
            HttpServletRequest httpServletRequest = ((AuthValveContext) event.getProperty("authContext")).getRequest();
            JahiaUser jahiaUser = (JahiaUser) event.getProperty("user");
            if (jahiaUser != null) {
                try {
                    jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, systemSession -> {
                        JCRNodeWrapper jcrUserNode = systemSession.getNode(jahiaUser.getLocalPath());
                        if (jcrUserNode == null) {
                            logger.error("User {} not found.", jahiaUser.getLocalPath());
                            return false;
                        }
                        return checkMyStravaProfileActivitiesFolder(jcrUserNode)
                                && saveTokenData(jcrUserNode, httpServletRequest);
                    });
                } catch (RepositoryException e) {
                    logger.error("", e);
                }
            }
        }
    }

    private boolean checkMyStravaProfileActivitiesFolder(JCRNodeWrapper jcrUserNode) {
        try {
            JCRNodeWrapper jcrNodeWrapper;
            if (!jcrUserNode.hasNode(MY_STRAVA_PROFILE_ACTIVITES_FOLDER)) {
                jcrNodeWrapper = jcrUserNode.addNode(MY_STRAVA_PROFILE_ACTIVITES_FOLDER, "jnt:contentFolder");
            } else {
                jcrNodeWrapper = jcrUserNode.getNode(MY_STRAVA_PROFILE_ACTIVITES_FOLDER);
            }
            JCRAutoSplitUtils.enableAutoSplitting(jcrNodeWrapper,
                    "date," + STRAVA_ACTIVITY_DATE + ",yyyy;date," + STRAVA_ACTIVITY_DATE + ",MM", "jnt:contentFolder");
            jcrNodeWrapper.saveSession();
            jcrPublicationService.publishByMainId(jcrNodeWrapper.getIdentifier());
            return true;
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return false;
    }

    private boolean saveTokenData(JCRNodeWrapper jcrUserNode, HttpServletRequest httpServletRequest) {
        try {
            if (jcrUserNode.hasProperty(JahiaOAuthConstants.TOKEN_DATA)) {
                Map<String, Object> tokenData = stravaClient.deserializeTokenData(jcrUserNode.getPropertyAsString(JahiaOAuthConstants.TOKEN_DATA));
                if (tokenData != null) {
                    httpServletRequest.getSession(false).setAttribute(JahiaOAuthConstants.TOKEN_DATA, tokenData);
                }
                jcrUserNode.getProperty(JahiaOAuthConstants.TOKEN_DATA).remove();
                jcrUserNode.saveSession();
                return true;
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return false;
    }
}
