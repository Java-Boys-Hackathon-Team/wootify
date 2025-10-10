package ru.javaboys.wootify.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WoofiFeignConfig {
    @Value("${woofi.bridge.api.key}")
    private String appApiKey;

    @Bean
    public RequestInterceptor woofiRequestInterceptor() {
        return template -> {
            if (appApiKey != null && !appApiKey.isEmpty()) {
                template.header("X-API-Key", appApiKey);
            }
        };
    }
}
