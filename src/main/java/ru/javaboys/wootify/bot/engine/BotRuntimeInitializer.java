package ru.javaboys.wootify.bot.engine;

import io.jmix.core.event.EntityChangedEvent;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.javaboys.wootify.entity.Bot;

import java.util.UUID;

/**
 * Создаёт строку состояния для каждого нового бота, чтобы UI и движок всегда работали с полной парой.
 */
public class BotRuntimeInitializer {

    private final BotRuntimeRepository runtime;

    public BotRuntimeInitializer(BotRuntimeRepository runtime) {
        this.runtime = runtime;
    }

    @TransactionalEventListener
    public void onBotChanged(EntityChangedEvent<Bot> event) {
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            runtime.ensureExists((UUID) event.getEntityId().getValue());
        }
    }
}
