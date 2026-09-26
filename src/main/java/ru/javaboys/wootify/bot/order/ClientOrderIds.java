package ru.javaboys.wootify.bot.order;

import ru.javaboys.wootify.entity.OrderRole;

import java.util.UUID;

/**
 * Детерминированные идентификаторы ордеров для биржи: по ним ордер находится после сбоя.
 * Формат {@code wb<бот>-<цикл>-<роль><уровень>-<попытка>}, не длиннее 36 символов (ограничение Orderly).
 */
public final class ClientOrderIds {

    private ClientOrderIds() {
    }

    public static String of(UUID botId, int cycle, OrderRole role, int level, long attempt) {
        String bot = botId.toString().replace("-", "").substring(0, 10);
        char r = switch (role) {
            case BASE -> 'B';
            case SAFETY -> 'S';
            case TAKE_PROFIT -> 'T';
            case CLOSE -> 'C';
            case MANUAL -> 'M';
        };
        return "wb" + bot + "-" + cycle + "-" + r + level + "-" + attempt;
    }
}
