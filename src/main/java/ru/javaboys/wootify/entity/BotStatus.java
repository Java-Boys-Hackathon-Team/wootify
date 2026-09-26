package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum BotStatus implements EnumClass<String> {

    STOPPED("STOPPED"),
    STARTING("STARTING"),
    RUNNING("RUNNING"),
    STOPPING("STOPPING"),
    SUSPENDED("SUSPENDED"),
    RESTARTING("RESTARTING"),
    FAILED("FAILED");

    private final String id;

    BotStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static BotStatus fromId(String id) {
        for (BotStatus at : BotStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
