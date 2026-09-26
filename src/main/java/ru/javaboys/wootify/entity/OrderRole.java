package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum OrderRole implements EnumClass<String> {

    BASE("BASE"),
    SAFETY("SAFETY"),
    TAKE_PROFIT("TAKE_PROFIT"),
    CLOSE("CLOSE"),
    MANUAL("MANUAL");

    private final String id;

    OrderRole(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static OrderRole fromId(String id) {
        for (OrderRole at : OrderRole.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
