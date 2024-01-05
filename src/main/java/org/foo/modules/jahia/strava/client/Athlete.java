package org.foo.modules.jahia.strava.client;

import java.time.LocalDateTime;
import java.util.List;

public class Athlete {
    private long id;
    private ResourceState resource_state;
    private String firstname;
    private String lastname;
    private String profile_medium; // URL to a 62x62 pixel profile picture.
    private String profile; // URL to a 124x124 pixel profile picture.
    private String city;
    private String state;
    private String country;
    private Sex sex;
    private boolean premium;
    private boolean summit;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private int follower_count;
    private int friend_count;
    private MeasurementPreference measurement_preference;
    private int ftp;
    private float weight;
    private List<Club> clubs;
    private List<Gear> bikes;
    private List<Gear> shoes;
}
