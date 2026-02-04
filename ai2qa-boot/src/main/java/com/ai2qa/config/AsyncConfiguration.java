package com.ai2qa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async task execution.
 *
 * <p>Provides a bounded thread pool to prevent unlimited thread creation
 * when using @Async annotations. Without this, Spring's default
 * SimpleAsyncTaskExecutor creates unlimited threads which could cause
 * issues under heavy load.
 *
 * <p>Thread pool sizing rationale:
 * <ul>
 *   <li>Max concurrent tests: 30 (from ai2qa.concurrent-limit.max-global)</li>
 *   <li>Async tasks per test completion: ~4 (report, memory, email, analytics)</li>
 *   <li>Worst case burst: 30 tests × 4 tasks = 120 tasks</li>
 *   <li>AI tasks (report, memory) are slow (10-30s), others are fast</li>
 * </ul>
 *
 * <p>Settings:
 * <ul>
 *   <li>Core pool: 8 threads (handles normal load)</li>
 *   <li>Max pool: 30 threads (matches max concurrent tests)</li>
 *   <li>Queue: 200 tasks (buffer for burst + slow AI tasks)</li>
 * </ul>
 */
@Configuration
public class AsyncConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

    /**
     * Creates the default async task executor.
     *
     * <p>This executor is used by all @Async methods unless they specify
     * a different executor by name.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        log.info("Configuring async task executor: core=8, max=30, queue=200");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        return executor;
    }
}
