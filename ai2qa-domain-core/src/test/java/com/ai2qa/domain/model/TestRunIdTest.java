package com.ai2qa.domain.model;

import com.ai2qa.domain.factory.TestRunIdFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TestRunId value object.
 */
class TestRunIdTest {

    @Test
    void shouldGenerateUniqueIds() {
        // When
        TestRunId id1 = TestRunIdFactory.generate();
        TestRunId id2 = TestRunIdFactory.generate();

        // Then
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    void shouldCreateFromValidString() {
        // Given
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString();

        // When
        Optional<TestRunId> result = TestRunIdFactory.fromString(uuidString);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo(uuid);
    }

    @Test
    void shouldReturnEmptyForNullString() {
        // When
        Optional<TestRunId> result = TestRunIdFactory.fromString(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForBlankString() {
        // When
        Optional<TestRunId> result = TestRunIdFactory.fromString("  ");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForInvalidUuid() {
        // When
        Optional<TestRunId> result = TestRunIdFactory.fromString("not-a-uuid");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHaveValueEquality() {
        // Given
        UUID uuid = UUID.randomUUID();
        TestRunId id1 = new TestRunId(uuid);
        TestRunId id2 = new TestRunId(uuid);

        // Then
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void shouldProvideStringRepresentation() {
        // Given
        UUID uuid = UUID.randomUUID();
        TestRunId id = new TestRunId(uuid);

        // When
        String result = id.toString();

        // Then
        assertThat(result).isEqualTo(uuid.toString());
    }
}
