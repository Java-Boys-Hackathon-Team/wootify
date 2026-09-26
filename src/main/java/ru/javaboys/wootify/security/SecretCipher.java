package ru.javaboys.wootify.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шифрование секретов в БД: AES-256-GCM, формат {@code enc:v1:<base64(iv | шифротекст | тег)>}.
 * <p>
 * Ключ устанавливается при старте приложения ({@link SecretEncryptionConfiguration}). Значения без
 * префикса считаются открытыми (данные до включения шифрования) и возвращаются как есть.
 * Статическое состояние нужно потому, что конвертеры JPA создаются вне контекста Spring.
 */
public final class SecretCipher {

    public static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec key;

    private SecretCipher() {
    }

    /**
     * @param base64Key 32 байта в base64 или {@code null}, чтобы хранить секреты открыто (только для разработки)
     */
    public static void install(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            key = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ключ шифрования должен быть 32 байта в base64 (openssl rand -base64 32)");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    public static boolean isEnabled() {
        return key != null;
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String encrypt(String plain) {
        SecretKeySpec k = key;
        if (plain == null || k == null || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось зашифровать секрет", e);
        }
    }

    public static String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            return stored;
        }
        SecretKeySpec k = key;
        if (k == null) {
            throw new IllegalStateException("Секрет зашифрован, но ключ шифрования не задан (WOOTIFY_ENCRYPTION_KEY)");
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            return new String(cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось расшифровать секрет: неверный ключ шифрования", e);
        }
    }
}
