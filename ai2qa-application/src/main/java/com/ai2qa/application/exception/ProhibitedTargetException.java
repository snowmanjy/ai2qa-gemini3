package com.ai2qa.application.exception;

/**
 * Thrown when an attempt is made to test a prohibited target domain.
 * This includes self-testing (ai2qa.com), government sites, and blacklisted
 * domains.
 */
public class ProhibitedTargetException extends RuntimeException {

    private final String blockedHost;

    public ProhibitedTargetException(String blockedHost) {
        super("Testing this domain is prohibited by safety policy: " + blockedHost);
        this.blockedHost = blockedHost;
    }

    public ProhibitedTargetException(String blockedHost, String reason) {
        super("Testing this domain is prohibited: " + reason);
        this.blockedHost = blockedHost;
    }

    public String getBlockedHost() {
        return blockedHost;
    }
}
