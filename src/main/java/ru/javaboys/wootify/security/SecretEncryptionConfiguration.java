package ru.javaboys.wootify.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Включает шифрование секретов ключей API и шифрует секреты, сохранённые до его включения.
 */
@Configuration
public class SecretEncryptionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecretEncryptionConfiguration.class);

    private final JdbcTemplate jdbc;

    public SecretEncryptionConfiguration(@Value("${wootify.security.encryption-key:}") String key,
                                         Environment environment, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        if ((key == null || key.isBlank()) && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Не задан ключ шифрования секретов WOOTIFY_ENCRYPTION_KEY");
        }
        SecretCipher.install(key);
        if (!SecretCipher.isEnabled()) {
            log.warn("Ключ шифрования не задан: секреты API хранятся открытым текстом (допустимо только для разработки)");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void encryptLegacySecrets() {
        if (!SecretCipher.isEnabled()) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ID, SECRET FROM API_KEY WHERE SECRET IS NOT NULL AND SECRET NOT LIKE 'enc:%'");
        for (Map<String, Object> row : rows) {
            jdbc.update("UPDATE API_KEY SET SECRET = ? WHERE ID = ?",
                    SecretCipher.encrypt((String) row.get("SECRET")), row.get("ID"));
        }
        if (!rows.isEmpty()) {
            log.info("Зашифровано секретов ключей API: {}", rows.size());
        }
    }
}
