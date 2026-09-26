package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum StopAction implements EnumClass<String> {

    KEEP_ORDERS("KEEP_ORDERS"),
    CANCEL_ORDERS("CANCEL_ORDERS"),
    CLOSE_POSITION("CLOSE_POSITION");

    private final String id;

    StopAction(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static StopAction fromId(String id) {
        for (StopAction at : StopAction.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
