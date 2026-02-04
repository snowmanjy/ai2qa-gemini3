package com.ai2qa.application.plan;

/**
 * Exception thrown when a test run cannot be found.
 * Mapped to HTTP 404 Not Found by the API layer.
 */
public class TestRunNotFoundException extends RuntimeException {

    private final String testRunId;

    public TestRunNotFoundException(String testRunId) {
        super("Test run not found: " + testRunId);
        this.testRunId = testRunId;
    }

    public String getTestRunId() {
        return testRunId;
    }
}
