package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum Network implements EnumClass<String> {

    MAINNET("MAINNET"),
    TESTNET("TESTNET");

    private final String id;

    Network(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static Network fromId(String id) {
        for (Network at : Network.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
