package com.nanopiva.citero.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor de emails: al saturarse (cola acotada por spring.task.execution.pool.*), corre el
 * task en el hilo llamador (CallerRuns) para aplicar backpressure en vez de agotar memoria.
 */
@Configuration
public class AsyncConfig {

    @Bean
    ThreadPoolTaskExecutorCustomizer emailExecutorCustomizer() {
        return executor ->
                executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
