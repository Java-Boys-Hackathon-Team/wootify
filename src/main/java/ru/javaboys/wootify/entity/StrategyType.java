package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum StrategyType implements EnumClass<String> {

    DCA("DCA");

    private final String id;

    StrategyType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static StrategyType fromId(String id) {
        for (StrategyType at : StrategyType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
