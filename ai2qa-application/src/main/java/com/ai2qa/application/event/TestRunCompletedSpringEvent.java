package com.ai2qa.application.event;

import com.ai2qa.domain.event.TestRunCompletedEvent;
import com.ai2qa.domain.model.TestRun;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent adapter for {@link TestRunCompletedEvent}.
 *
 * <p>This bridges the pure domain event to Spring's event infrastructure,
 * allowing Spring components to listen using @EventListener.
 */
public class TestRunCompletedSpringEvent extends ApplicationEvent {

    private final TestRunCompletedEvent domainEvent;

    public TestRunCompletedSpringEvent(Object source, TestRunCompletedEvent domainEvent) {
        super(source);
        this.domainEvent = domainEvent;
    }

    public TestRunCompletedSpringEvent(Object source, TestRun testRun) {
        super(source);
        this.domainEvent = TestRunCompletedEvent.of(testRun);
    }

    public TestRunCompletedEvent getDomainEvent() {
        return domainEvent;
    }

    public TestRun getTestRun() {
        return domainEvent.testRun();
    }
}
