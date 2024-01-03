package org.foo.modules.jahia.strava.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component(service = StravaClient.class)
public class StravaClient {
    private static final Logger logger = LoggerFactory.getLogger(StravaClient.class);

    private final ObjectMapper objectMapper;

    public StravaClient() {
        objectMapper = new ObjectMapper();
    }

    public String serializeTokenData(Map<String, Object> tokenData) throws JsonProcessingException {
        return objectMapper.writeValueAsString(tokenData);
    }

    public Map<String, Object> deserializeTokenData(String tokenData) throws JsonProcessingException {
        if (StringUtils.isBlank(tokenData)) {
            return null;
        }
        return objectMapper.readValue(tokenData, new TypeReference<Map<String, Object>>() {
        });
    }
}
