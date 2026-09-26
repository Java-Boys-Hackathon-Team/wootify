package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum EventLevel implements EnumClass<String> {

    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR");

    private final String id;

    EventLevel(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static EventLevel fromId(String id) {
        for (EventLevel at : EventLevel.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
