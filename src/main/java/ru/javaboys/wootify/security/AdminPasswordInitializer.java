package ru.javaboys.wootify.security;

import io.jmix.core.UnconstrainedDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.javaboys.wootify.entity.User;

/**
 * Устанавливает пароль администратора из окружения (WOOTIFY_ADMIN_PASSWORD), чтобы в развёрнутой
 * системе не оставался пароль по умолчанию.
 */
@Component
public class AdminPasswordInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordInitializer.class);

    private final String password;
    private final UnconstrainedDataManager dataManager;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordInitializer(@Value("${wootify.security.admin-password:}") String password,
                                    UnconstrainedDataManager dataManager, PasswordEncoder passwordEncoder) {
        this.password = password;
        this.dataManager = dataManager;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void apply() {
        if (password == null || password.isBlank()) {
            return;
        }
        dataManager.load(User.class).query("select u from User u where u.username = 'admin'").optional()
                .ifPresent(admin -> {
                    if (!passwordEncoder.matches(password, admin.getPassword())) {
                        admin.setPassword(passwordEncoder.encode(password));
                        dataManager.save(admin);
                        log.info("Пароль администратора обновлён из окружения");
                    }
                });
    }
}
