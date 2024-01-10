package org.foo.modules.jahia.strava.oauth;

import org.foo.modules.jahia.strava.actions.SyncBackgroundJob;
import org.foo.modules.jahia.strava.client.StravaClient;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.services.content.JCRNodeWrapper;
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

    @Reference
    private JCRTemplate jcrTemplate;
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
                        return SyncBackgroundJob.checkMyStravaProfileActivitiesFolder(jcrUserNode)
                                && saveTokenData(jcrUserNode, httpServletRequest);
                    });
                } catch (RepositoryException e) {
                    logger.error("", e);
                }
            }
        }
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
