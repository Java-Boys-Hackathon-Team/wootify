package ru.javaboys.wootify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Планировщик для {@code @Scheduled}-задач приложения (симулятор биржи, очистка журнала).
 * Jmix объявляет собственные планировщики; явный бин с именем taskScheduler снимает неоднозначность.
 */
@Configuration
public class SchedulingConfiguration {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("wootify-scheduler-");
        return scheduler;
    }
}
