package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum CycleCloseReason implements EnumClass<String> {

    TAKE_PROFIT("TAKE_PROFIT"),
    STOP_LOSS("STOP_LOSS"),
    MANUAL("MANUAL"),
    EXTERNAL("EXTERNAL");

    private final String id;

    CycleCloseReason(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static CycleCloseReason fromId(String id) {
        for (CycleCloseReason at : CycleCloseReason.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
