package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.foo.modules.jahia.strava.oauth.StravaApi20;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.jahia.services.notification.HttpClientService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component(service = StravaClient.class)
public class StravaClient {
    private static final Logger logger = LoggerFactory.getLogger(StravaClient.class);

    public static final String TOKEN_EXPIRES_AT = "expiresAt";
    public static final int TOKEN_MIN_LIFE_IN_SECONDS = 60;

    private final ObjectMapper objectMapper;
    @Reference
    private HttpClientService httpClientService;

    public StravaClient() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new SimpleModule().addDeserializer(LatLng.class, new LatLngDeserializer(LatLng.class)));
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Map<String, Object> addExpireAtInTokenData(Map<String, Object> tokenData) {
        tokenData.put(TOKEN_EXPIRES_AT, (System.currentTimeMillis() / 1000) + (Integer) tokenData.get(JahiaOAuthConstants.TOKEN_EXPIRES_IN));
        return tokenData;
    }

    public String serializeTokenData(Map<String, Object> tokenData) {
        try {
            return objectMapper.writeValueAsString(addExpireAtInTokenData(tokenData));
        } catch (JsonProcessingException e) {
            logger.error("", e);
        }
        return null;
    }

    public Map<String, Object> deserializeTokenData(String tokenData) {
        try {
            if (StringUtils.isNotBlank(tokenData)) {
                return objectMapper.readValue(tokenData, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            }
        } catch (JsonProcessingException e) {
            logger.error("", e);
        }
        return null;
    }

    private Optional<Activity> getActivity(String accessToken, long id) {
        try {
            String response = httpClientService.executeGet(StravaApi20.API + "/api/v3/activities/" + id + "?include_all_efforts=true", Collections.singletonMap(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
            if (response != null) {
                return Optional.ofNullable(objectMapper.readValue(response, Activity.class));
            }
        } catch (JsonProcessingException e) {
            logger.error("", e);
        }
        return Optional.empty();
    }

    private Optional<List<Activity>> getActivities(String accessToken, int page) {
        try {
            String response = httpClientService.executeGet(StravaApi20.API + "/api/v3/athlete/activities?page=" + page, Collections.singletonMap(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
            if (response != null) {
                return Optional.ofNullable(objectMapper.readValue(response, objectMapper.getTypeFactory().constructCollectionType(List.class, Activity.class)));
            }
        } catch (JsonProcessingException e) {
            logger.error("", e);
        }
        return Optional.empty();
    }

    public void syncMe(String accessToken) {
        logger.info("Sync me");
        List<Activity> activities = new ArrayList<>();
        int page = 1;
        getActivities(accessToken, page).ifPresent(data -> {
            data.forEach(activity -> getActivity(accessToken, activity.getId()).ifPresent(activities::add));
            // getMyActivities(accessToken, page + 1, activities);
        });
        logger.info("Activities: {}", activities);
    }
}
