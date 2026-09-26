package ru.javaboys.wootify.test_support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Общая конфигурация интеграционных тестов: реальный PostgreSQL, управляемые котировки.
 * Все тесты используют один контекст Spring.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestExchangeConfig.class)
public @interface IntegrationTest {
}
