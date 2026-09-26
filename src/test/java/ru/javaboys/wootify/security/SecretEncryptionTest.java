package ru.javaboys.wootify.security;

import io.jmix.core.UnconstrainedDataManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.javaboys.wootify.entity.ApiKey;
import ru.javaboys.wootify.test_support.IntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class SecretEncryptionTest {

    @Autowired
    UnconstrainedDataManager dataManager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SecretEncryptionConfiguration encryption;

    @Test
    void cipherRoundTripUsesRandomIv() {
        String a = SecretCipher.encrypt("top-secret");
        String b = SecretCipher.encrypt("top-secret");
        assertThat(a).startsWith(SecretCipher.PREFIX).isNotEqualTo(b);
        assertThat(SecretCipher.decrypt(a)).isEqualTo("top-secret");
        assertThat(SecretCipher.decrypt("legacy-plain")).isEqualTo("legacy-plain");
    }

    @Test
    void apiKeySecretIsEncryptedAtRest() {
        ApiKey key = dataManager.create(ApiKey.class);
        key.setName("enc-" + UUID.randomUUID());
        key.setKey("ed25519:public");
        key.setSecret("very-secret-value");
        dataManager.save(key);

        String stored = jdbc.queryForObject("SELECT SECRET FROM API_KEY WHERE ID = ?", String.class, key.getId());
        assertThat(stored).startsWith(SecretCipher.PREFIX).doesNotContain("very-secret-value");
        assertThat(dataManager.load(ApiKey.class).id(key.getId()).one().getSecret()).isEqualTo("very-secret-value");
    }

    @Test
    void legacyPlaintextSecretsAreEncryptedOnStartup() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO API_KEY (ID, NAME, SECRET) VALUES (?, ?, ?)", id, "legacy-" + id, "plain-secret");
        encryption.encryptLegacySecrets();
        assertThat(jdbc.queryForObject("SELECT SECRET FROM API_KEY WHERE ID = ?", String.class, id))
                .startsWith(SecretCipher.PREFIX);
        assertThat(dataManager.load(ApiKey.class).id(id).one().getSecret()).isEqualTo("plain-secret");
    }
}
