package com.ai2qa.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to enforce a global daily cap on test runs.
 * Used for hackathon demo to prevent abuse without complex rate limiting.
 */
@Service
public class DailyCapService {

    private static final Logger log = LoggerFactory.getLogger(DailyCapService.class);

    private final AtomicInteger dailyCount = new AtomicInteger(0);
    private volatile LocalDate lastReset = LocalDate.now();

    @Value("${ai2qa.daily-cap:20}")
    private int dailyCap;

    /**
     * Check if a new test run can be executed.
     *
     * @return true if under the daily cap, false if limit reached
     */
    public synchronized boolean canExecute() {
        resetIfNewDay();
        return dailyCount.get() < dailyCap;
    }

    /**
     * Increment the daily count after a test run is created.
     */
    public synchronized void increment() {
        resetIfNewDay();
        int count = dailyCount.incrementAndGet();
        log.info("Daily test run count: {}/{}", count, dailyCap);
    }

    /**
     * Get the number of remaining test runs for today.
     *
     * @return remaining runs available
     */
    public int remaining() {
        resetIfNewDay();
        return Math.max(0, dailyCap - dailyCount.get());
    }

    /**
     * Get the current daily cap setting.
     *
     * @return the configured daily cap
     */
    public int getDailyCap() {
        return dailyCap;
    }

    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastReset)) {
            log.info("New day detected, resetting daily count. Previous count: {}", dailyCount.get());
            dailyCount.set(0);
            lastReset = today;
        }
    }
}
