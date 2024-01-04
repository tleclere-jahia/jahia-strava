package org.foo.modules.jahia.strava.actions;

import org.foo.modules.jahia.strava.client.StravaClient;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.bin.Render;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@Component(service = Action.class)
public class SyncMeAction extends Action {
    private static final Logger logger = LoggerFactory.getLogger(SyncMeAction.class);

    @Reference
    private StravaClient stravaClient;

    public SyncMeAction() {
        setName("syncMe");
        setRequiredMethods(Render.METHOD_GET);
    }

    @Override
    public ActionResult doExecute(HttpServletRequest httpServletRequest, RenderContext renderContext, Resource resource, JCRSessionWrapper jcrSessionWrapper, Map<String, List<String>> parameters, URLResolver urlResolver) {
        Map<String, Object> tokenData = (Map<String, Object>) renderContext.getRequest().getSession(false).getAttribute(JahiaOAuthConstants.TOKEN_DATA);
        if (tokenData != null) {
            stravaClient.syncMe((String) tokenData.get(JahiaOAuthConstants.ACCESS_TOKEN));
        }
        return new ActionResult(HttpServletResponse.SC_OK, resource.getNode().getPath());
    }
}
