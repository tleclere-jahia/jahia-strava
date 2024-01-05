package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PolylineMapDeserializer extends StdDeserializer<PolylineMap> {
    private static final long serialVersionUID = -267992923891957902L;

    protected PolylineMapDeserializer(Class<?> vc) {
        super(vc);
    }

    private static List<LatLng> decode(String encoded) {
        //precision
        double inv = 1.0 / 1e5;
        List<LatLng> decoded = new ArrayList<>();
        byte[] previous = new byte[]{0, 0};
        int i = 0;
        //for each byte
        while (i < encoded.length()) {
            //for each coord (lat, lon)
            byte[] ll = new byte[]{0, 0};
            for (int j = 0; j < 2; j++) {
                byte shift = 0;
                byte b = 0x20;
                //keep decoding bytes until you have this coord
                while (b >= 0x20) {
                    b = (byte) (encoded.charAt(i++) - 63);
                    ll[j] |= (byte) ((b & 0x1f) << shift);
                    shift += 5;
                }
                //add previous offset to get final value and remember for next one
                ll[j] = (byte) (previous[j] + ((ll[j] & 1) == 1 ? ~(ll[j] >> 1) : (ll[j] >> 1)));
                previous[j] = ll[j];
            }
            //scale by precision and chop off long coords also flip the positions so
            //its the far more standard lon,lat instead of lat,lon
            decoded.add(new LatLng(ll[0] * inv, ll[1] * inv));
        }
        //hand back the list of coordinates
        return decoded;
    }

    @Override
    public PolylineMap deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        return new PolylineMap(
                node.get("id").asText(),
                node.has("polyline") ? decode(node.get("polyline").asText()) : Collections.emptyList(),
                decode(node.get("summary_polyline").asText()));
    }
}
