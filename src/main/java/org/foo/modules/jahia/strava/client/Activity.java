package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public class Activity {
    private long id;
    private ResourceState resource_state;
    private Athlete athlete;
    private String external_id;
    private long upload_id;
    private String name;
    private float distance;
    private int moving_time;
    private int elapsed_time;
    private float total_elevation_gain;
    private float elev_high;
    private float elev_low;
    private SportType sport_type;
    private LocalDateTime start_date;
    private LocalDateTime start_date_local;
    private String timezone;
    public LatLng start_latlng;
    public LatLng end_latlng;
    private int achievement_count;
    private int kudos_count;
    private int comment_count;
    private int athlete_count;
    private int photo_count;
    private int total_photo_count;
    private PolylineMap map;
    private boolean trainer;
    private boolean commute;
    private boolean manual;
    @JsonProperty("private")
    private boolean isPrivate;
    private boolean flagged;
    private int workout_type;
    private String upload_id_str;
    private float average_speed;
    private float max_speed;
    private boolean has_kudoed;
    private boolean hide_from_home;
    private String gear_id;
    private float kilojoules;
    private float average_watts;
    private boolean device_watts;
    private int max_watts;
    private int weighted_average_watts;
    private String description;
    private PhotosSummary photos;
    private Gear gear;
    private float calories;
    private List<SegmentEffort> segment_efforts;
    private String device_name;
    private String embed_token;
    private List<Split> splits_metric;
    private List<Split> splits_standard;
    private List<Lap> laps;
    private List<SegmentEffort> best_efforts;

    public long getId() {
        return id;
    }
}
