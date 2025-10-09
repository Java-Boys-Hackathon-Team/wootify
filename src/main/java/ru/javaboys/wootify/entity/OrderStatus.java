package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum OrderStatus implements EnumClass<String> {

    NEW("NEW"),
    FILLED("FILLED"),
    PARTIAL_FILLED("PARTIAL_FILLED"),
    CANCELLED("CANCELLED"),
    CREATED("CREATED");

    private final String id;

    OrderStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OrderStatus fromId(String id) {
        for (OrderStatus at : OrderStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}