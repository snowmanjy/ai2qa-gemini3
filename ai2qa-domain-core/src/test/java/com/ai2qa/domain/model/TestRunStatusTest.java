package com.ai2qa.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TestRunStatus enum.
 */
class TestRunStatusTest {

    @Test
    void shouldIdentifyTerminalStates() {
        assertThat(TestRunStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TestRunStatus.FAILED.isTerminal()).isTrue();
        assertThat(TestRunStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TestRunStatus.TIMEOUT.isTerminal()).isTrue();

        assertThat(TestRunStatus.PENDING.isTerminal()).isFalse();
        assertThat(TestRunStatus.PLANNING.isTerminal()).isFalse();
        assertThat(TestRunStatus.RUNNING.isTerminal()).isFalse();
        assertThat(TestRunStatus.PAUSED.isTerminal()).isFalse();
    }

    @Test
    void shouldIdentifyActiveStates() {
        assertThat(TestRunStatus.RUNNING.isActive()).isTrue();
        assertThat(TestRunStatus.PLANNING.isActive()).isTrue();

        assertThat(TestRunStatus.PENDING.isActive()).isFalse();
        assertThat(TestRunStatus.PAUSED.isActive()).isFalse();
        assertThat(TestRunStatus.COMPLETED.isActive()).isFalse();
        assertThat(TestRunStatus.FAILED.isActive()).isFalse();
    }

    @Test
    void shouldIdentifyCancellableStates() {
        // Non-terminal states can be cancelled
        assertThat(TestRunStatus.PENDING.canCancel()).isTrue();
        assertThat(TestRunStatus.PLANNING.canCancel()).isTrue();
        assertThat(TestRunStatus.RUNNING.canCancel()).isTrue();
        assertThat(TestRunStatus.PAUSED.canCancel()).isTrue();

        // Terminal states cannot be cancelled
        assertThat(TestRunStatus.COMPLETED.canCancel()).isFalse();
        assertThat(TestRunStatus.FAILED.canCancel()).isFalse();
        assertThat(TestRunStatus.CANCELLED.canCancel()).isFalse();
        assertThat(TestRunStatus.TIMEOUT.canCancel()).isFalse();
    }

    @Test
    void shouldIdentifyResumableStates() {
        assertThat(TestRunStatus.PAUSED.canResume()).isTrue();

        assertThat(TestRunStatus.PENDING.canResume()).isFalse();
        assertThat(TestRunStatus.RUNNING.canResume()).isFalse();
        assertThat(TestRunStatus.COMPLETED.canResume()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TestRunStatus.class)
    void shouldHaveConsistentTerminalAndCancelBehavior(TestRunStatus status) {
        // If terminal, then not cancellable
        if (status.isTerminal()) {
            assertThat(status.canCancel()).isFalse();
        }
    }
}
