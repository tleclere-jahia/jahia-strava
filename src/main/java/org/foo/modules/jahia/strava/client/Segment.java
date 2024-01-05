package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;

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
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private float total_elevation_gain;
    private PolylineMap map;
    private int effort_count;
    private int athlete_count;
    private boolean hazardous;
    private int star_count;
}
