package org.foo.modules.jahia.strava.oauth;

import org.apache.commons.lang.StringUtils;
import org.foo.modules.jahia.strava.StravaApi;
import org.foo.modules.jahia.strava.client.StravaClient;
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
import java.util.OptionalLong;

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
        if(logger.isDebugEnabled()) {
            logger.debug("OAuth results: {}", results);
        }
        if (results.containsKey(StravaApi.SSO_LOGIN_ID)) {
            String username = (String) results.get(StravaApi.SSO_LOGIN_ID);
            if (username == null) {
                logger.error("Username {} not found", username);
                return;
            }
            try {
                jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, session -> {
                    JCRUserNode jcrUserNode = jahiaUserManagerService.lookupUser(username, StravaApi.SITEKEY, session);
                    if (jcrUserNode == null) {
                        logger.error("User {} not found", username);
                        return false;
                    }
                    try {
                        saveTokenData(jcrUserNode, results);
                        setMembership(jcrUserNode);
                        setStravaProfile(jcrUserNode, results);
                        return true;
                    } catch (RepositoryException e) {
                        logger.error("", e);
                        return false;
                    }
                });
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
    }

    private void saveTokenData(JCRUserNode jcrUserNode, Map<String, Object> results) throws RepositoryException {
        if (results.containsKey(JahiaOAuthConstants.TOKEN_DATA)) {
            String tokenData = stravaClient.serializeTokenData((Map<String, Object>) results.get(JahiaOAuthConstants.TOKEN_DATA));
            if (StringUtils.isNotBlank(tokenData)) {
                jcrUserNode.setProperty(JahiaOAuthConstants.TOKEN_DATA, tokenData);
                jcrUserNode.saveSession();
            }
        }
    }

    /**
     * Fills stravaUrl from the athlete who owns the token, so that a user who cannot edit their
     * profile still gets their map: StravaProfileListener creates it once stravaUrl is saved. The
     * connector maps only the profile fields its mappings name, so the athlete id is read from Strava.
     */
    private void setStravaProfile(JCRUserNode jcrUserNode, Map<String, Object> results) throws RepositoryException {
        Map<String, Object> tokenData = (Map<String, Object>) results.get(JahiaOAuthConstants.TOKEN_DATA);
        String accessToken = tokenData != null ? (String) tokenData.get(JahiaOAuthConstants.ACCESS_TOKEN) : null;
        if (StringUtils.isBlank(accessToken)) {
            return;
        }
        OptionalLong athleteId = stravaClient.getAthleteId(accessToken);
        if (!athleteId.isPresent()) {
            logger.warn("Cannot read the Strava athlete of {}: stravaUrl stays as it is", jcrUserNode.getName());
            return;
        }
        String stravaUrl = StravaApi.STRAVA_ATHLETE_URL + athleteId.getAsLong();
        if (!jcrUserNode.isNodeType(StravaApi.STRAVA_PROFILE)) {
            jcrUserNode.addMixin(StravaApi.STRAVA_PROFILE);
        } else if (stravaUrl.equals(jcrUserNode.getPropertyAsString(StravaApi.STRAVA_URL))) {
            return;
        }
        jcrUserNode.setProperty(StravaApi.STRAVA_URL, stravaUrl);
        jcrUserNode.saveSession();
    }

    private void setMembership(JCRUserNode jcrUserNode) throws RepositoryException {
        if (!jcrUserNode.isMemberOfGroup(StravaApi.SITEKEY, StravaApi.GROUPNAME)) {
            JCRGroupNode jcrGroupNode = jahiaGroupManagerService.lookupGroup(StravaApi.SITEKEY, StravaApi.GROUPNAME, jcrUserNode.getSession());
            if (jcrGroupNode == null) {
                jcrGroupNode = jahiaGroupManagerService.createGroup(StravaApi.SITEKEY, StravaApi.GROUPNAME, null, false, jcrUserNode.getSession());
            }
            jcrGroupNode.addMember(jcrUserNode);
            jcrGroupNode.saveSession();
        }
    }
}
