package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum OrderStatus implements EnumClass<String> {

    CREATED("CREATED"),
    SENT_OPEN("SENT_OPEN"),
    OPEN("OPEN"),
    SENT_CANCEL("SENT_CANCEL"),
    CANCELLED("CANCELLED"),
    CLOSED("CLOSED"),
    ERROR("ERROR");

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