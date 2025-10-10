package ru.javaboys.wootify.orderly.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PriceData {
    private final long ts;
    private final String topic;
    private final Data data;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public PriceData(
            @JsonProperty("ts") long ts,
            @JsonProperty("topic") String topic,
            @JsonProperty("data") Data data
    ) {
        this.ts = ts;
        this.topic = topic;
        this.data = data;
    }

    public long getTs() {
        return ts;
    }

    public String getTopic() {
        return topic;
    }

    public Data getData() {
        return data;
    }

    @Override
    public String toString() {
        return "PriceData{" +
               "ts=" + ts +
               ", topic='" + topic + '\'' +
               ", data=" + data +
               '}';
    }

    public static class Data {
        private final BigDecimal ask;
        private final BigDecimal askSize;
        private final BigDecimal bid;
        private final BigDecimal bidSize;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Data(
                @JsonProperty("ask") BigDecimal ask,
                @JsonProperty("askSize") BigDecimal askSize,
                @JsonProperty("bid") BigDecimal bid,
                @JsonProperty("bidSize") BigDecimal bidSize
        ) {
            this.ask = ask;
            this.askSize = askSize;
            this.bid = bid;
            this.bidSize = bidSize;
        }

        public BigDecimal getAsk() {
            return ask;
        }

        public BigDecimal getAskSize() {
            return askSize;
        }

        public BigDecimal getBid() {
            return bid;
        }

        public BigDecimal getBidSize() {
            return bidSize;
        }

        @Override
        public String toString() {
            return "Data{" +
                   "ask=" + ask +
                   ", askSize=" + askSize +
                   ", bid=" + bid +
                   ", bidSize=" + bidSize +
                   '}';
        }
    }
}
