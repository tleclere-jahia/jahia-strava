package org.foo.modules.jahia.strava.utils;

import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;

import java.util.Map;

public final class RequestUtils {
    private RequestUtils() {
        // Constructor disabled for utility class
    }

    public static final String SESSION_REQUEST_URI = "my.request_uri";
    public static final String TOKEN_EXPIRES_AT = "expiresAt";
    public static final long TOKEN_MIN_LIFE_IN_SECONDS = 60L;

    public static Map<String, Object> addExpireAtInTokenData(Map<String, Object> tokenData) {
        tokenData.put(TOKEN_EXPIRES_AT, (System.currentTimeMillis() / 1000L) + (Integer) tokenData.get(JahiaOAuthConstants.TOKEN_EXPIRES_IN));
        return tokenData;
    }
}
