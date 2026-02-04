package com.ai2qa.application.util;

import org.springframework.stereotype.Component;

/**
 * Abstraction for Thread.sleep to enable testing.
 */
public interface Sleeper {
    void sleep(long millis);

    @Component
    class DefaultSleeper implements Sleeper {
        @Override
        public void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during sleep", e);
            }
        }
    }
}
