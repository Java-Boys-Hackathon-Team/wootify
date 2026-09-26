package ru.javaboys.wootify.common;

/**
 * Ошибка конфигурации бота: повторный запуск без изменения настроек не поможет,
 * поэтому движок сразу переводит бота в состояние FAILED.
 */
public class BotConfigurationException extends RuntimeException {

    public BotConfigurationException(String message) {
        super(message);
    }
}
