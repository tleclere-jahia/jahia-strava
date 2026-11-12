package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Reads the ISO-8601 dates of the Strava API, such as 2023-12-31T07:53:58Z. The runtime exports no
 * jackson-datatype-jsr310, and jackson-databind refuses java.time types without a handler.
 */
public class LocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {
    private static final long serialVersionUID = 3326482416839548157L;

    public LocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        String text = jsonParser.getValueAsString();
        if (text == null || text.isEmpty()) {
            return null;
        }
        // ISO_DATE_TIME accepts the trailing offset (Z) and keeps the wall-clock time as given
        return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
    }
}
