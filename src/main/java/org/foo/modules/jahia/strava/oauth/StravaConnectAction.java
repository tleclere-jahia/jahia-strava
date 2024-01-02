package org.foo.modules.jahia.strava.oauth;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.foo.modules.jahia.strava.utils.RequestUtils;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.bin.Render;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component(service = Action.class)
public class StravaConnectAction extends Action {
    private static final String NAME = "connectToStrava";

    @Reference
    private JahiaOAuthService jahiaOAuthService;
    @Reference
    private SettingsService settingsService;

    @Activate
    public void onActivate() {
        setName(NAME);
        setRequireAuthenticatedUser(false);
        setRequiredMethods(Render.METHOD_GET);
    }

    @Override
    public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource, JCRSessionWrapper session,
                                  Map<String, List<String>> parameters, URLResolver urlResolver) throws Exception {

        final String sessionId = req.getSession().getId();

        String referer = req.getHeader(HttpHeaders.REFERER);
        if (StringUtils.isNotBlank(referer)) {
            req.getSession(false).setAttribute(RequestUtils.SESSION_REQUEST_URI, referer);
        }

        ConnectorConfig connectorConfig = settingsService.getConnectorConfig(renderContext.getSite().getSiteKey(), StravaConnector.KEY);
        if (connectorConfig == null) {
            return ActionResult.BAD_REQUEST;
        }
        String authorizationUrl = jahiaOAuthService.getAuthorizationUrl(connectorConfig, sessionId, Collections.emptyMap());
        if (authorizationUrl == null) {
            return ActionResult.BAD_REQUEST;
        }
        return new ActionResult(HttpServletResponse.SC_OK, authorizationUrl, true, null);
    }
}
