package org.foo.modules.jahia.strava.filters;

import org.foo.modules.jahia.strava.client.StravaClient;
import org.foo.modules.jahia.strava.oauth.StravaConnector;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;

@Component(service = RenderFilter.class)
public class RefreshTokenDataFilter extends AbstractFilter {
    @Reference
    private JahiaOAuthService jahiaOAuthService;
    @Reference
    private SettingsService settingsService;
    @Reference
    private StravaClient stravaClient;

    public RefreshTokenDataFilter() {
        setApplyOnConfigurations(Resource.CONFIGURATION_PAGE);
        setPriority(0.0f);
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        ConnectorConfig connectorConfig = settingsService.getConnectorConfig(renderContext.getSite().getSiteKey(), StravaConnector.KEY);
        if (connectorConfig == null) {
            return null;
        }
        Map<String, Object> tokenData = (Map<String, Object>) renderContext.getRequest().getSession(false).getAttribute(JahiaOAuthConstants.TOKEN_DATA);
        if (tokenData != null && (((Integer) tokenData.get(StravaClient.TOKEN_EXPIRES_AT) - (System.currentTimeMillis() / 1000)) <= StravaClient.TOKEN_MIN_LIFE_IN_SECONDS)) {
            renderContext.getRequest().getSession(false).setAttribute(JahiaOAuthConstants.TOKEN_DATA,
                    stravaClient.addExpireAtInTokenData(jahiaOAuthService.refreshAccessToken(connectorConfig, (String) tokenData.get(JahiaOAuthConstants.REFRESH_TOKEN))));
        }
        return null;
    }
}
