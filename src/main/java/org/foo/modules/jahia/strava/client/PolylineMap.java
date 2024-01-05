package org.foo.modules.jahia.strava.client;

import java.util.List;

public class PolylineMap {
    private final String id;
    private final List<LatLng> polyline;
    private final List<LatLng> summaryPolyline;

    public PolylineMap(String id, List<LatLng> polyline, List<LatLng> summaryPolyline) {
        this.id = id;
        this.polyline = polyline;
        this.summaryPolyline = summaryPolyline;
    }

    public String getPolyline() {
        StringBuilder sb = new StringBuilder("[");
        for (LatLng latLng : polyline) {
            sb.append("[").append(latLng.getLat()).append(",").append(latLng.getLng()).append("]");
        }
        sb.append("]");
        return sb.toString();
    }
}
