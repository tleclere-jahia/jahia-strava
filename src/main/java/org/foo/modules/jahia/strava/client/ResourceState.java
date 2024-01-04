package org.foo.modules.jahia.strava.client;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceState {
    META(1),
    SUMMARY(2),
    DETAIL(3);

    private final int state;

    ResourceState(int state) {
        this.state = state;
    }

    @JsonValue
    public int getState() {
        return state;
    }
}
