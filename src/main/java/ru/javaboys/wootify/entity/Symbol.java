package ru.javaboys.wootify.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@JmixEntity
@Table(name = "SYMBOL")
@Entity
public class Symbol {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "ANALOG_TICKER")
    private String analogTicker;

    @InstanceName
    @Column(name = "NAME")
    private String name;

    @Column(name = "WOOFI_TICKER")
    private String woofiTicker;

    public String getAnalogTicker() {
        return analogTicker;
    }

    public void setAnalogTicker(String analogTicker) {
        this.analogTicker = analogTicker;
    }

    public String getWoofiTicker() {
        return woofiTicker;
    }

    public void setWoofiTicker(String woofiTicker) {
        this.woofiTicker = woofiTicker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}