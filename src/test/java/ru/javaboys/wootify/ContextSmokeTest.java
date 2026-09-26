package ru.javaboys.wootify;

import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.wootify.test_support.IntegrationTest;
import ru.javaboys.wootify.entity.Symbol;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ContextSmokeTest {

    @Autowired
    UnconstrainedDataManager dataManager;

    @Test
    void contextLoads() {
        assertThat(dataManager.load(Symbol.class).all().list()).isNotNull();
    }
}
