package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum TradeType implements EnumClass<String> {

    LONG("LONG"),
    SHORT("SHORT");

    private final String id;

    TradeType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static TradeType fromId(String id) {
        for (TradeType at : TradeType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}