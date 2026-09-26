package ru.javaboys.wootify.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum PaperOrderStatus implements EnumClass<String> {

    NEW("NEW"),
    FILLED("FILLED"),
    CANCELLED("CANCELLED"),
    REJECTED("REJECTED");

    private final String id;

    PaperOrderStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static PaperOrderStatus fromId(String id) {
        for (PaperOrderStatus at : PaperOrderStatus.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}
