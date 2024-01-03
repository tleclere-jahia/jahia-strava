package org.foo.modules.jahia.strava.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.foo.modules.jahia.strava.utils.RequestUtils;
import org.foo.modules.jahia.strava.utils.StravaClient;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.ConnectorResultProcessor;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.services.content.decorator.JCRGroupNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Map;

@Component(service = ConnectorResultProcessor.class, immediate = true)
public class StravaConnectorResultProcessor implements ConnectorResultProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StravaConnectorResultProcessor.class);

    @Reference
    private JahiaUserManagerService jahiaUserManagerService;
    @Reference
    private JahiaGroupManagerService jahiaGroupManagerService;
    @Reference
    private JCRTemplate jcrTemplate;
    @Reference
    private StravaClient stravaClient;

    @Override
    public void execute(ConnectorConfig connectorConfig, Map<String, Object> results) {
        logger.info("OAuth results: {}", results);
        if (results.containsKey(StravaApi20.SSO_LOGIN_ID)) {
            String username = (String) results.get(StravaApi20.SSO_LOGIN_ID);
            if (username == null) {
                logger.error("Username {} not found", username);
                return;
            }
            try {
                jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                    JCRUserNode user = jahiaUserManagerService.lookupUser(username, StravaApi20.SITEKEY, session);
                    if (user == null) {
                        logger.error("User {} not found", username);
                        return false;
                    }
                    try {
                        if (results.containsKey(JahiaOAuthConstants.TOKEN_DATA)) {
                            saveTokenData(user, (Map<String, Object>) results.get(JahiaOAuthConstants.TOKEN_DATA));
                        }
                        setMembership(user);
                        return true;
                    } catch (JsonProcessingException | RepositoryException e) {
                        logger.error("", e);
                        return false;
                    }
                });
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
    }

    private void saveTokenData(JCRUserNode jcrUserNode, Map<String, Object> tokenData) throws JsonProcessingException, RepositoryException {
        jcrUserNode.setProperty(JahiaOAuthConstants.TOKEN_DATA, stravaClient.serializeTokenData(RequestUtils.addExpireAtInTokenData(tokenData)));
        jcrUserNode.saveSession();
    }

    private void setMembership(JCRUserNode jcrUserNode) throws RepositoryException {
        if (!jcrUserNode.isMemberOfGroup(StravaApi20.SITEKEY, StravaApi20.GROUPNAME)) {
            JCRGroupNode jcrGroupNode = jahiaGroupManagerService.lookupGroup(StravaApi20.SITEKEY, StravaApi20.GROUPNAME, jcrUserNode.getSession());
            if (jcrGroupNode == null) {
                jcrGroupNode = jahiaGroupManagerService.createGroup(StravaApi20.SITEKEY, StravaApi20.GROUPNAME, null, false, jcrUserNode.getSession());
            }
            jcrGroupNode.addMember(jcrUserNode);
            jcrGroupNode.saveSession();
        }
    }
}
