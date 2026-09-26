package ru.javaboys.wootify.bot.engine;

/**
 * Этот экземпляр больше не владеет ботом: запись в БД отклонена проверкой владельца.
 */
class OwnershipLostException extends RuntimeException {

    OwnershipLostException() {
        super("Владение ботом потеряно", null, false, false);
    }
}
