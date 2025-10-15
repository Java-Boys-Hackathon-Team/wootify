package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum DesiredState implements EnumClass<String> {

    RUNNING("RUNNING"),
    STOPPED("STOPPED");

    private final String id;

    DesiredState(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static DesiredState fromId(String id) {
        for (DesiredState at : DesiredState.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}