package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum EventCategory implements EnumClass<String> {

    LIFECYCLE("LIFECYCLE"),
    ORDER("ORDER"),
    CYCLE("CYCLE"),
    MARKET("MARKET"),
    SYSTEM("SYSTEM");

    private final String id;

    EventCategory(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static EventCategory fromId(String id) {
        for (EventCategory at : EventCategory.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
