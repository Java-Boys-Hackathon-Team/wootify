package ru.javaboys.wootify;

import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.javaboys.wootify.entity.Symbol;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContextSmokeTest {

    @Autowired
    UnconstrainedDataManager dataManager;

    @Test
    void contextLoads() {
        assertThat(dataManager.load(Symbol.class).all().list()).isNotNull();
    }
}
