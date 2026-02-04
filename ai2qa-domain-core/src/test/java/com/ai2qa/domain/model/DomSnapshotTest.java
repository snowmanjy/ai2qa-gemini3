package com.ai2qa.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DomSnapshot record.
 */
class DomSnapshotTest {

    @Test
    void shouldCreateSnapshotWithContent() {
        // When
        DomSnapshot snapshot = DomSnapshot.of("line1\nline2\nline3", "http://test.com", "Test Page");

        // Then
        assertThat(snapshot.content()).isEqualTo("line1\nline2\nline3");
        assertThat(snapshot.url()).isEqualTo("http://test.com");
        assertThat(snapshot.title()).isEqualTo("Test Page");
        assertThat(snapshot.capturedAt()).isNotNull();
    }

    @Test
    void shouldCreateEmptySnapshot() {
        // When
        DomSnapshot snapshot = DomSnapshot.empty();

        // Then
        assertThat(snapshot.content()).isEmpty();
        assertThat(snapshot.url()).isEmpty();
        assertThat(snapshot.title()).isEmpty();
        assertThat(snapshot.hasContent()).isFalse();
    }

    @Test
    void shouldIdentifyHasContent() {
        // Given
        DomSnapshot withContent = DomSnapshot.of("content", "url", "title");
        DomSnapshot empty = DomSnapshot.empty();
        DomSnapshot blankContent = DomSnapshot.of("  ", "url", "title");

        // Then
        assertThat(withContent.hasContent()).isTrue();
        assertThat(empty.hasContent()).isFalse();
        assertThat(blankContent.hasContent()).isFalse();
    }

    @Test
    void shouldReturnContentOptionalWhenPresent() {
        // Given
        DomSnapshot snapshot = DomSnapshot.of("content", "url", "title");

        // When
        Optional<String> result = snapshot.contentOpt();

        // Then
        assertThat(result).contains("content");
    }

    @Test
    void shouldReturnEmptyContentOptionalWhenBlank() {
        // Given
        DomSnapshot snapshot = DomSnapshot.of("  ", "url", "title");

        // When
        Optional<String> result = snapshot.contentOpt();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldCountLines() {
        // Given
        DomSnapshot multiLine = DomSnapshot.of("line1\nline2\nline3", "url", "title");
        DomSnapshot singleLine = DomSnapshot.of("single line", "url", "title");
        DomSnapshot empty = DomSnapshot.empty();

        // Then
        assertThat(multiLine.lineCount()).isEqualTo(3);
        assertThat(singleLine.lineCount()).isEqualTo(1);
        assertThat(empty.lineCount()).isZero();
    }

    @Test
    void shouldExtractContextAroundKeyword() {
        // Given
        String content = """
                line 1
                line 2
                target keyword here
                line 4
                line 5
                """;
        DomSnapshot snapshot = DomSnapshot.of(content, "url", "title");

        // When
        Optional<String> result = snapshot.extractContext("keyword", 1);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).contains("line 2");
        assertThat(result.get()).contains("target keyword here");
        assertThat(result.get()).contains("line 4");
    }

    @Test
    void shouldReturnEmptyWhenKeywordNotFound() {
        // Given
        DomSnapshot snapshot = DomSnapshot.of("some content", "url", "title");

        // When
        Optional<String> result = snapshot.extractContext("notfound", 1);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleNullInExtractContext() {
        // Given
        DomSnapshot snapshot = DomSnapshot.of("content", "url", "title");

        // When
        Optional<String> result = snapshot.extractContext(null, 1);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldCheckContainsTextCaseInsensitive() {
        // Given
        DomSnapshot snapshot = DomSnapshot.of("Login Button", "url", "title");

        // Then
        assertThat(snapshot.containsText("login")).isTrue();
        assertThat(snapshot.containsText("LOGIN")).isTrue();
        assertThat(snapshot.containsText("Button")).isTrue();
        assertThat(snapshot.containsText("notfound")).isFalse();
    }

    @Test
    void shouldHandleNullContentInContainsText() {
        // Given
        DomSnapshot snapshot = new DomSnapshot(null, "url", "title", null);

        // Then
        assertThat(snapshot.containsText("anything")).isFalse();
    }
}
