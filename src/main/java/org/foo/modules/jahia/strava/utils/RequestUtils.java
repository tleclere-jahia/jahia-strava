package org.foo.modules.jahia.strava.utils;

import org.jahia.exceptions.JahiaException;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

public final class RequestUtils {
    private RequestUtils() {
        // Constructor disabled for utility class
    }

    public static final String SESSION_REQUEST_URI = "my.request_uri";
    public static final String TOKEN_EXPIRES_AT = "expiresAt";
    public static final long TOKEN_MIN_LIFE_IN_SECONDS = 60L;

    public static String getSiteKey(HttpServletRequest httpServletRequest, JahiaSitesService jahiaSitesService) {
        String siteKey;
        try {
            JahiaSite jahiaSite = jahiaSitesService.getSiteByServerName(httpServletRequest.getServerName());
            if (jahiaSite != null) {
                siteKey = jahiaSite.getSiteKey();
            } else {
                siteKey = jahiaSitesService.getDefaultSite().getSiteKey();
            }
        } catch (JahiaException e) {
            siteKey = jahiaSitesService.getDefaultSite().getSiteKey();
        }
        return siteKey;
    }

    public static Map<String, Object> addExpireAtInTokenData(Map<String, Object> tokenData) {
        tokenData.put(TOKEN_EXPIRES_AT, (System.currentTimeMillis() / 1000L) + (Integer) tokenData.get(JahiaOAuthConstants.TOKEN_EXPIRES_IN));
        return tokenData;
    }
}
