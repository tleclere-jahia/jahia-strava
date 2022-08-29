package org.foo.modules.jahia.strava.oauth;

import org.jahia.modules.jahiaauth.service.JahiaAuthMapperService;
import org.jahia.modules.jahiaauth.service.MappedProperty;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.params.valves.AuthValveContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Component(service = EventHandler.class, property = {"event.topics=org/jahia/usersgroups/login/LOGIN"})
public class StravaLoginListener implements EventHandler {
    private static final Logger logger = LoggerFactory.getLogger(StravaLoginListener.class);

    private JahiaAuthMapperService jahiaAuthMapperService;

    @Reference
    private void setJahiaAuthMapperService(JahiaAuthMapperService jahiaAuthMapperService) {
        this.jahiaAuthMapperService = jahiaAuthMapperService;
    }

    @Override
    public void handleEvent(Event event) {
        if ("org/jahia/usersgroups/login/LOGIN".equals(event.getTopic())) {
            HttpServletRequest httpServletRequest = ((AuthValveContext) event.getProperty("authContext")).getRequest();
            Map<String, MappedProperty> tokenData = jahiaAuthMapperService.getMapperResultsForSession(httpServletRequest.getRequestedSessionId()).get(JahiaOAuthConstants.TOKEN_DATA);
            if (tokenData != null && tokenData.containsKey(JahiaOAuthConstants.TOKEN_DATA)) {
                httpServletRequest.getSession(false).setAttribute(JahiaOAuthConstants.TOKEN_DATA, tokenData.get(JahiaOAuthConstants.TOKEN_DATA).getValue());
            }
        }
    }
}
