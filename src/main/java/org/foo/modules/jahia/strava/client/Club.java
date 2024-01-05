package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Club {
    private long id;
    private ResourceState resource_state;
    private String name;
    private String profile_medium; // URL to a 60x60 pixel profile picture.
    private String cover_photo; // URL to a ~1185x580 pixel cover photo.
    private String cover_photo_small; // URL to a ~360x176 pixel cover photo.
    private SportType sport_type;
    private List<ActivityType> activity_types;
    private String city;
    private String state;
    private String country;
    @JsonProperty("private")
    private boolean isPrivate;
    private int member_count;
    private boolean featured;
    private boolean verified;
    private String url;
    private Membership membership;
    private boolean admin;
    private boolean owner;
    private int following_count;
}
