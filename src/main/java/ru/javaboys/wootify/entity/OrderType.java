package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum OrderType implements EnumClass<String> {

    LIMIT("LIMIT"),
    MARKET("MARKET");

    private final String id;

    OrderType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OrderType fromId(String id) {
        for (OrderType at : OrderType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}