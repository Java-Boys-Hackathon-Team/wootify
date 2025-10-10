package ru.javaboys.wootify.orderly.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Event {
    private final String event;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Event(
            @JsonProperty("event") String event
    ) {
        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "Event{" +
               "event='" + event + '\'' +
               '}';
    }
}
