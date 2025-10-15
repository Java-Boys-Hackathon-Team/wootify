package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum RuntimeState implements EnumClass<String> {

    WAIT_RUNNING("WAIT_RUNNING"),
    RUNNING("RUNNING"),
    WAIT_STOPPED("WAIT_STOPPED"),
    STOPPED("STOPPED"),
    ERROR("ERROR");

    private final String id;

    RuntimeState(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static RuntimeState fromId(String id) {
        for (RuntimeState at : RuntimeState.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}