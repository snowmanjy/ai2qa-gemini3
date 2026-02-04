package com.ai2qa.application.cache;

import com.ai2qa.domain.model.CachedSelector;
import com.ai2qa.domain.port.SelectorCachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SelectorCacheService.
 */
@ExtendWith(MockitoExtension.class)
class SelectorCacheServiceTest {

    @Mock
    private SelectorCachePort cachePort;

    @Mock
    private Clock clock;

    private SelectorCacheService service;

    private static final String TENANT_ID = "test-tenant";
    private static final String GOAL_TEXT = "click the login button";
    private static final String URL = "https://example.com/login";
    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(FIXED_TIME);
        service = new SelectorCacheService(cachePort, clock);
    }

    @Test
    void shouldHashGoalTextConsistently() {
        // Given
        String goal = "click the submit button";

        // When
        String hash1 = service.hashGoal(goal);
        String hash2 = service.hashGoal(goal);

        // Then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex chars
    }

    @Test
    void shouldHashGoalCaseInsensitively() {
        // When
        String hash1 = service.hashGoal("Click The Button");
        String hash2 = service.hashGoal("click the button");

        // Then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldHashGoalAfterTrimming() {
        // When
        String hash1 = service.hashGoal("  click button  ");
        String hash2 = service.hashGoal("click button");

        // Then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldRejectNullGoalText() {
        assertThatThrownBy(() -> service.hashGoal(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankGoalText() {
        assertThatThrownBy(() -> service.hashGoal("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNormalizeUrlByRemovingQueryParams() {
        // When
        String normalized = service.normalizeUrl("https://example.com/page?foo=bar&baz=qux");

        // Then
        assertThat(normalized).isEqualTo("https://example.com/page");
    }

    @Test
    void shouldNormalizeUrlByRemovingFragment() {
        // When
        String normalized = service.normalizeUrl("https://example.com/page#section");

        // Then
        assertThat(normalized).isEqualTo("https://example.com/page");
    }

    @Test
    void shouldNormalizeUrlByRemovingTrailingSlash() {
        // When
        String normalized = service.normalizeUrl("https://example.com/page/");

        // Then
        assertThat(normalized).isEqualTo("https://example.com/page");
    }

    @Test
    void shouldNormalizeUrlByReplacingUuidWithPlaceholder() {
        // When
        String normalized = service.normalizeUrl(
                "https://example.com/users/550e8400-e29b-41d4-a716-446655440000/profile");

        // Then
        assertThat(normalized).isEqualTo("https://example.com/users/{id}/profile");
    }

    @Test
    void shouldNormalizeUrlByReplacingNumericIdWithPlaceholder() {
        // When
        String normalized = service.normalizeUrl("https://example.com/products/12345/details");

        // Then
        assertThat(normalized).isEqualTo("https://example.com/products/{id}/details");
    }

    @Test
    void shouldNormalizeMultipleIds() {
        // When
        String normalized = service.normalizeUrl(
                "https://example.com/orders/123/items/456");

        // Then
        assertThat(normalized).isEqualTo("https://example.com/orders/{id}/items/{id}");
    }

    @Test
    void shouldRejectNullUrl() {
        assertThatThrownBy(() -> service.normalizeUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFindCachedSelector() {
        // Given
        CachedSelector cached = CachedSelector.create(
                UUID.randomUUID(), TENANT_ID, "hash", "pattern", "#button", "Button", FIXED_TIME);
        when(cachePort.find(eq(TENANT_ID), anyString(), anyString()))
                .thenReturn(Optional.of(cached));

        // When
        Optional<CachedSelector> result = service.findCached(TENANT_ID, GOAL_TEXT, URL);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().selector()).isEqualTo("#button");
    }

    @Test
    void shouldCacheSelector() {
        // When
        service.cacheSelector(TENANT_ID, GOAL_TEXT, URL, "#submit-btn", "Submit button");

        // Then
        ArgumentCaptor<CachedSelector> captor = ArgumentCaptor.forClass(CachedSelector.class);
        verify(cachePort).save(captor.capture());

        CachedSelector saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.selector()).isEqualTo("#submit-btn");
        assertThat(saved.elementDescription()).isEqualTo("Submit button");
        assertThat(saved.createdAt()).isEqualTo(FIXED_TIME);
        // Goal hash should be derived from goal text
        assertThat(saved.goalHash()).hasSize(64);
    }

    @Test
    void shouldRecordSuccess() {
        // When
        service.recordSuccess(TENANT_ID, GOAL_TEXT, URL);

        // Then
        verify(cachePort).recordSuccess(eq(TENANT_ID), anyString(), anyString(), eq(FIXED_TIME));
    }

    @Test
    void shouldRecordFailure() {
        // When
        service.recordFailure(TENANT_ID, GOAL_TEXT, URL);

        // Then
        verify(cachePort).recordFailure(eq(TENANT_ID), anyString(), anyString(), eq(FIXED_TIME));
    }

    @Test
    void shouldInvalidateSelector() {
        // When
        service.invalidate(TENANT_ID, GOAL_TEXT, URL);

        // Then
        verify(cachePort).invalidate(eq(TENANT_ID), anyString(), anyString());
    }

    @Test
    void shouldCleanupStale() {
        // Given
        when(cachePort.cleanupStale(30)).thenReturn(5);

        // When
        int deleted = service.cleanupStale(30);

        // Then
        assertThat(deleted).isEqualTo(5);
        verify(cachePort).cleanupStale(30);
    }
}
