package org.foo.modules.jahia.strava;

import java.time.format.DateTimeFormatter;

public class StravaApi {

    public static final String API = "https://www.strava.com";
    public static final String SSO_LOGIN_ID = "username";
    public static final String GROUPNAME = "strava-users";
    public static final String SITEKEY = null;
    public static final String MY_STRAVA_PROFILE_ACTIVITES_FOLDER = "strava-activities";
    public static final String STRAVA_ACTIVITY = "foont:stravaActivity";
    public static final String STRAVA_ACTIVITIES = "foont:stravaActivities";
    public static final String STRAVA_ACTIVITY_DATE = "date";
    public static final String STRAVA_ACTIVITY_JSON = "jsonValue";
    public static final String LAST_STRAVA_SYNC = "lastStravaSync";
    public static final DateTimeFormatter LAST_STRAVA_SYNC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private StravaApi(){
        throw new IllegalStateException("Utility class");
    }
}
