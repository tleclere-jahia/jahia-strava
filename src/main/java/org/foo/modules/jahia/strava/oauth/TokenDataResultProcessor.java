package org.foo.modules.jahia.strava.oauth;

import org.foo.modules.jahia.strava.utils.RequestUtils;
import org.jahia.modules.jahiaauth.service.*;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collections;
import java.util.Map;

@Component(service = ConnectorResultProcessor.class, immediate = true)
public class TokenDataResultProcessor implements ConnectorResultProcessor {
    private JahiaAuthMapperService jahiaAuthMapperService;

    @Reference
    private void setJahiaAuthMapperService(JahiaAuthMapperService jahiaAuthMapperService) {
        this.jahiaAuthMapperService = jahiaAuthMapperService;
    }

    @Override
    public void execute(ConnectorConfig connectorConfig, Map<String, Object> results) {
        // store tokenData to cache
        jahiaAuthMapperService.cacheMapperResults(JahiaOAuthConstants.TOKEN_DATA, RequestContextHolder.getRequestAttributes().getSessionId(),
                Collections.singletonMap(JahiaOAuthConstants.TOKEN_DATA, new MappedProperty(
                        new MappedPropertyInfo(JahiaOAuthConstants.TOKEN_DATA),
                        RequestUtils.addExpireAtInTokenData((Map<String, Object>) results.get(JahiaOAuthConstants.TOKEN_DATA)))));
    }
}
