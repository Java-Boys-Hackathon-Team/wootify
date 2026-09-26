package ru.javaboys.wootify.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Конфигурация Feign-клиентов терминала. Не помечена {@code @Configuration}: подключается
 * только к клиентам, которые явно на неё ссылаются.
 */
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
