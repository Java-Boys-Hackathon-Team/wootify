package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum CycleStatus implements EnumClass<String> {

    OPEN("OPEN"),
    CLOSING("CLOSING"),
    CLOSED("CLOSED");

    private final String id;

    CycleStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static CycleStatus fromId(String id) {
        for (CycleStatus at : CycleStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
