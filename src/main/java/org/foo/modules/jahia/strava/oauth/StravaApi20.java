package org.foo.modules.jahia.strava.oauth;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;

import java.time.format.DateTimeFormatter;

public final class StravaApi20 extends DefaultApi20 {
    public static final String API = "https://www.strava.com";
    public static final String SSO_LOGIN_ID = "username";
    public static final String GROUPNAME = "strava-users";
    public static final String SITEKEY = null;
    public static final String MY_STRAVA_PROFILE_ACTIVITES_FOLDER = "strava-activities";
    public static final String STRAVA_ACTIVITY_DATE = "date";
    public static final String STRAVA_ACTIVITY_JSON = "jsonValue";
    public static final String LAST_STRAVA_SYNC = "lastStravaSync";
    public static final DateTimeFormatter LAST_STRAVA_SYNC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private StravaApi20() {
    }

    private static class InstanceHolder {
        private static final StravaApi20 INSTANCE = new StravaApi20();
    }

    public static StravaApi20 instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return API + "/oauth/token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return API + "/oauth/authorize";
    }

    @Override
    public ClientAuthentication getClientAuthentication() {
        return RequestBodyAuthenticationScheme.instance();
    }
}
