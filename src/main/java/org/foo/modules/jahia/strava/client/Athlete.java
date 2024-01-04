package org.foo.modules.jahia.strava.client;

import java.time.LocalDateTime;

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
}
