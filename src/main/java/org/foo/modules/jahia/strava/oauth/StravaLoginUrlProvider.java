package org.foo.modules.jahia.strava.oauth;

import org.foo.modules.jahia.strava.utils.RequestUtils;
import org.jahia.exceptions.JahiaException;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.osgi.BundleUtils;
import org.jahia.params.valves.LoginUrlProvider;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

@Component(service = LoginUrlProvider.class, immediate = true)
public class StravaLoginUrlProvider implements LoginUrlProvider {
    private static final Logger logger = LoggerFactory.getLogger(StravaLoginUrlProvider.class);

    private SettingsService settingsService;
    private JahiaOAuthService jahiaOAuthService;
    private JahiaSitesService jahiaSitesService;
    private String moduleId;

    public StravaLoginUrlProvider() {
        moduleId = null;
    }

    @Reference
    private void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Reference
    private void setJahiaOAuthService(JahiaOAuthService jahiaOAuthService) {
        this.jahiaOAuthService = jahiaOAuthService;
    }

    @Reference
    private void setJahiaSitesService(JahiaSitesService jahiaSitesService) {
        this.jahiaSitesService = jahiaSitesService;
    }

    @Activate
    private void onActivate(BundleContext bundleContext) {
        moduleId = BundleUtils.getModule(bundleContext.getBundle()).getId();
    }

    @Override
    public boolean hasCustomLoginUrl() {
        return true;
    }

    private String getAuthorizationUrl(String siteKey, String sessionId) {
        ConnectorConfig connectorConfig = settingsService.getConnectorConfig(siteKey, StravaConnector.KEY);
        if (connectorConfig == null) {
            return null;
        }
        return jahiaOAuthService.getAuthorizationUrl(connectorConfig, sessionId, null);
    }

    @Override
    public String getLoginUrl(HttpServletRequest httpServletRequest) {
        String siteKey = RequestUtils.getSiteKey(httpServletRequest, jahiaSitesService);
        if (siteKey == null) {
            return null;
        }

        try {
            if (jahiaSitesService.getSiteByKey(siteKey).getInstalledModules().stream().noneMatch(module -> module.equals(moduleId))) {
                logger.warn("Module {} is not enabled for the site {}", moduleId, siteKey);
                return null;
            }

            String authorizationUrl = getAuthorizationUrl(siteKey, httpServletRequest.getRequestedSessionId());
            if (authorizationUrl == null) {
                logger.warn("AuthorizationUrl is not set to site {}", siteKey);
                return null;
            }

            // save the requestUri in the session
            String originalRequestUri = (String) httpServletRequest.getAttribute("javax.servlet.error.request_uri");
            if (originalRequestUri == null) {
                originalRequestUri = httpServletRequest.getRequestURI();
            }
            httpServletRequest.getSession(false).setAttribute(RequestUtils.SESSION_REQUEST_URI, originalRequestUri);
            // redirect to SSO
            return authorizationUrl;
        } catch (JahiaException e) {
            logger.error("", e);
        }
        return null;
    }
}
