package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum OrderSide implements EnumClass<String> {

    BUY("BUY"),
    SELL("SELL");

    private final String id;

    OrderSide(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OrderSide fromId(String id) {
        for (OrderSide at : OrderSide.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}