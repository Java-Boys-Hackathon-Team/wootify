package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum BotSettingsState implements EnumClass<String> {

    INACTIVE("INACTIVE"),
    ACTIVE("ACTIVE"),
    FINAL("FINAL");

    private final String id;

    BotSettingsState(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static BotSettingsState fromId(String id) {
        for (BotSettingsState at : BotSettingsState.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}