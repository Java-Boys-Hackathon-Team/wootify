package ru.javaboys.wootify.common;

/**
 * Маркер временного сбоя: сеть, перегрузка биржи, нет свежих цен.
 * Движок ботов повторяет такт после паузы, не считая такой сбой падением бота.
 */
public interface TransientFailure {
}
