package ru.javaboys.wootify.exchange.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import ru.javaboys.wootify.exchange.ExchangeException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Конфигурация клиента моста: заголовок доступа и перевод HTTP-ошибок в {@link ExchangeException}.
 * Не помечена {@code @Configuration}, чтобы не применяться к остальным Feign-клиентам.
 */
public class BridgeFeignConfig {

    @Bean
    public RequestInterceptor bridgeAuthInterceptor(@Value("${woofi.bridge.api.key}") String bridgeApiKey) {
        return template -> {
            if (bridgeApiKey != null && !bridgeApiKey.isEmpty()) {
                template.header("X-API-Key", bridgeApiKey);
            }
        };
    }

    @Bean
    public ErrorDecoder bridgeErrorDecoder() {
        return new BridgeErrorDecoder();
    }

    static final class BridgeErrorDecoder implements ErrorDecoder {
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public Exception decode(String methodKey, Response response) {
            String detail = readDetail(response);
            ExchangeException.Kind kind = switch (response.status()) {
                case 401, 403 -> ExchangeException.Kind.AUTH;
                case 404 -> ExchangeException.Kind.NOT_FOUND;
                case 400, 422 -> ExchangeException.Kind.REJECTED;
                case 429, 500, 503, 504 -> ExchangeException.Kind.TRANSIENT;
                default -> ExchangeException.Kind.UNKNOWN;
            };
            return ExchangeException.of(kind, "Мост ответил " + response.status() + ": " + detail, null);
        }

        private String readDetail(Response response) {
            if (response.body() == null) {
                return "";
            }
            try (InputStream in = response.body().asInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                try {
                    JsonNode node = mapper.readTree(body);
                    String type = node.path("error_type").asText("");
                    String detail = node.path("detail").asText(body);
                    return type.isEmpty() ? detail : type + ": " + detail;
                } catch (Exception e) {
                    return body;
                }
            } catch (Exception e) {
                return "";
            }
        }
    }

    /** Сетевые сбои Feign (нет соединения, таймаут) - временные. */
    static ExchangeException translate(RuntimeException e) {
        if (e instanceof ExchangeException ee) {
            return ee;
        }
        if (e instanceof RetryableException) {
            return ExchangeException.of(ExchangeException.Kind.TRANSIENT, "Мост недоступен: " + e.getMessage(), e);
        }
        return ExchangeException.of(ExchangeException.Kind.UNKNOWN, "Ошибка вызова моста: " + e.getMessage(), e);
    }
}
