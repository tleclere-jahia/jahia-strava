package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class Segment {
    private long id;
    private String name;
    private ActivityType activity_type;
    private float distance;
    private float average_grade;
    private float maximum_grade;
    private float elevation_high;
    private float elevation_low;
    @JsonDeserialize(using = LatLngDeserializer.class)
    private LatLng start_latlng;
    @JsonDeserialize(using = LatLngDeserializer.class)
    private LatLng end_latlng;
    private int climb_category;
    private String city;
    private String state;
    private String country;
    @JsonProperty("private")
    private boolean isPrivate;
    private SegmentEffort athlete_pr_effort;
    private SegmentEffort athlete_segment_stats;
}
