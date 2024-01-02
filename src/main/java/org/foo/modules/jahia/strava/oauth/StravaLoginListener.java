package org.foo.modules.jahia.strava.oauth;

import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.jahiaauth.service.JahiaAuthMapperService;
import org.jahia.modules.jahiaauth.service.MappedProperty;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.services.content.JCRAutoSplitUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.usermanager.JahiaUser;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Component(service = EventHandler.class, property = {"event.topics=org/jahia/usersgroups/login/LOGIN"})
public class StravaLoginListener implements EventHandler {
    private static final Logger logger = LoggerFactory.getLogger(StravaLoginListener.class);

    private static final String MY_STRAVA_PROFILE_ACTIVITES_FOLDER = "strava-activities";
    private static final String STRAVA_ACTIVITY_DATE = "date";

    @Reference
    private JahiaAuthMapperService jahiaAuthMapperService;
    @Reference
    private JCRTemplate jcrTemplate;
    @Reference
    private JCRPublicationService jcrPublicationService;

    @Override
    public void handleEvent(Event event) {
        if ("org/jahia/usersgroups/login/LOGIN".equals(event.getTopic())) {
            HttpServletRequest httpServletRequest = ((AuthValveContext) event.getProperty("authContext")).getRequest();
            JahiaUser jahiaUser = (JahiaUser) event.getProperty("user");
            Map<String, MappedProperty> tokenData = jahiaAuthMapperService.getMapperResultsForSession(httpServletRequest.getRequestedSessionId()).get(JahiaOAuthConstants.TOKEN_DATA);
            if (jahiaUser != null && tokenData != null && tokenData.containsKey(JahiaOAuthConstants.TOKEN_DATA)) {
                checkMyStravaProfileActivitiesFolder(jahiaUser.getLocalPath());
                httpServletRequest.getSession(false).setAttribute(JahiaOAuthConstants.TOKEN_DATA, tokenData.get(JahiaOAuthConstants.TOKEN_DATA).getValue());
            }
        }
    }

    private void checkMyStravaProfileActivitiesFolder(String jahiaUserPath) {
        try {
            jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, systemSession -> {
                JCRNodeWrapper jcrNodeWrapper = systemSession.getNode(jahiaUserPath);
                if (jcrNodeWrapper == null) {
                    logger.warn("User {} not found.", jahiaUserPath);
                    return null;
                }
                if (!jcrNodeWrapper.hasNode(MY_STRAVA_PROFILE_ACTIVITES_FOLDER)) {
                    jcrNodeWrapper = jcrNodeWrapper.addNode(MY_STRAVA_PROFILE_ACTIVITES_FOLDER, "jnt:contentFolder");
                } else {
                    jcrNodeWrapper = jcrNodeWrapper.getNode(MY_STRAVA_PROFILE_ACTIVITES_FOLDER);
                }
                JCRAutoSplitUtils.enableAutoSplitting(jcrNodeWrapper,
                        "date," + STRAVA_ACTIVITY_DATE + ",yyyy;date," + STRAVA_ACTIVITY_DATE + ",MM", "jnt:contentFolder");
                jcrNodeWrapper.saveSession();
                jcrPublicationService.publishByMainId(jcrNodeWrapper.getIdentifier());
                return null;
            });
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }
}
