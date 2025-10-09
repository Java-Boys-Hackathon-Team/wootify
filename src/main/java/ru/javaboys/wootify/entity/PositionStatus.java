package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum PositionStatus implements EnumClass<String> {

    CREATED("CREATED"),
    OPENED("OPENED"),
    CLOSED("CLOSED"),
    CANCELLED("CANCELLED");

    private final String id;

    PositionStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static PositionStatus fromId(String id) {
        for (PositionStatus at : PositionStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}