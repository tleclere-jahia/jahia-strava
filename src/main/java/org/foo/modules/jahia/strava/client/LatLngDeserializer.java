package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

public class LatLngDeserializer extends StdDeserializer<LatLng> {
    private static final long serialVersionUID = 6047989770896980980L;

    protected LatLngDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public LatLng deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        ArrayNode node = jsonParser.getCodec().readTree(jsonParser);
        if (node == null || node.size() != 2) {
            return null;
        }
        return new LatLng(node.get(0).doubleValue(), node.get(1).doubleValue());
    }
}
