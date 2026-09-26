package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum TradingMode implements EnumClass<String> {

    PAPER("PAPER"),
    LIVE("LIVE");

    private final String id;

    TradingMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static TradingMode fromId(String id) {
        for (TradingMode at : TradingMode.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
