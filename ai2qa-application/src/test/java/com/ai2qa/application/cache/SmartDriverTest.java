package com.ai2qa.application.cache;

import com.ai2qa.domain.model.CachedSelector;
import com.ai2qa.domain.model.DomSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SmartDriver.
 */
@ExtendWith(MockitoExtension.class)
class SmartDriverTest {

    @Mock
    private SelectorCacheService cacheService;

    @Mock
    private SmartDriver.SelectorFinder aiSelectorFinder;

    private SmartDriver smartDriver;

    private static final String TENANT_ID = "test-tenant";
    private static final String ELEMENT_DESCRIPTION = "click the login button";
    private static final String URL = "https://example.com/login";
    private static final DomSnapshot SNAPSHOT = DomSnapshot.of(
            "<html><button id='login'>Login</button></html>",
            URL,
            "Login Page"
    );

    @BeforeEach
    void setUp() {
        smartDriver = new SmartDriver(cacheService, aiSelectorFinder);
    }

    @Test
    void shouldUseCachedSelectorWhenAvailableAndValid() {
        // Given - Cache hit
        CachedSelector cached = createCachedSelector("#login-btn", 5, 0);
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.of(cached));

        // Verifier says selector is valid
        Function<String, Boolean> verifier = selector -> true;

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElement(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT, verifier
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().selector()).isEqualTo("#login-btn");
        assertThat(result.get().fromCache()).isTrue();
        assertThat(result.get().cacheSuccessCount()).isEqualTo(6); // 5 + 1

        // Should record success
        verify(cacheService).recordSuccess(TENANT_ID, ELEMENT_DESCRIPTION, URL);

        // Should NOT call AI
        verifyNoInteractions(aiSelectorFinder);
    }

    @Test
    void shouldFallBackToAiWhenCachedSelectorFails() {
        // Given - Cache hit but selector doesn't work
        CachedSelector cached = createCachedSelector("#old-btn", 3, 0);
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.of(cached));

        // Verifier fails for cached selector
        Function<String, Boolean> verifier = selector ->
                !"#old-btn".equals(selector) && "#new-btn".equals(selector);

        // AI returns new selector
        when(aiSelectorFinder.findSelector(ELEMENT_DESCRIPTION, SNAPSHOT))
                .thenReturn(Optional.of("#new-btn"));

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElement(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT, verifier
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().selector()).isEqualTo("#new-btn");
        assertThat(result.get().fromCache()).isFalse();

        // Should record failure for cached selector
        verify(cacheService).recordFailure(TENANT_ID, ELEMENT_DESCRIPTION, URL);

        // Should cache new selector
        verify(cacheService).cacheSelector(
                eq(TENANT_ID), eq(ELEMENT_DESCRIPTION), eq(URL),
                eq("#new-btn"), eq(ELEMENT_DESCRIPTION)
        );
    }

    @Test
    void shouldCallAiOnCacheMiss() {
        // Given - Cache miss
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.empty());

        // AI returns selector
        when(aiSelectorFinder.findSelector(ELEMENT_DESCRIPTION, SNAPSHOT))
                .thenReturn(Optional.of("#login-button"));

        // Verifier accepts selector
        Function<String, Boolean> verifier = selector -> true;

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElement(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT, verifier
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().selector()).isEqualTo("#login-button");
        assertThat(result.get().fromCache()).isFalse();

        // Should call AI
        verify(aiSelectorFinder).findSelector(ELEMENT_DESCRIPTION, SNAPSHOT);

        // Should cache the result
        verify(cacheService).cacheSelector(
                eq(TENANT_ID), eq(ELEMENT_DESCRIPTION), eq(URL),
                eq("#login-button"), eq(ELEMENT_DESCRIPTION)
        );
    }

    @Test
    void shouldReturnEmptyWhenAiCannotFindSelector() {
        // Given - Cache miss
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.empty());

        // AI returns empty
        when(aiSelectorFinder.findSelector(ELEMENT_DESCRIPTION, SNAPSHOT))
                .thenReturn(Optional.empty());

        Function<String, Boolean> verifier = selector -> true;

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElement(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT, verifier
        );

        // Then
        assertThat(result).isEmpty();

        // Should NOT cache anything
        verify(cacheService, never()).cacheSelector(any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnEmptyWhenAiSelectorFailsVerification() {
        // Given - Cache miss
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.empty());

        // AI returns selector that doesn't work
        when(aiSelectorFinder.findSelector(ELEMENT_DESCRIPTION, SNAPSHOT))
                .thenReturn(Optional.of("#broken-selector"));

        // Verifier rejects
        Function<String, Boolean> verifier = selector -> false;

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElement(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT, verifier
        );

        // Then
        assertThat(result).isEmpty();

        // Should NOT cache broken selector
        verify(cacheService, never()).cacheSelector(any(), any(), any(), any(), any());
    }

    @Test
    void shouldHandleVerifierException() {
        // Given - Cache hit
        CachedSelector cached = createCachedSelector("#btn", 2, 0);
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.of(cached));

        // Verifier throws exception (e.g., element not found)
        Function<String, Boolean> verifier = selector -> {
            throw new RuntimeException("Element not found");
        };

        // AI returns new selector
        when(aiSelectorFinder.findSelector(ELEMENT_DESCRIPTION, SNAPSHOT))
                .thenReturn(Optional.of("#new-btn"));

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElement(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT, verifier
        );

        // Then - Should fall back to AI (but AI selector also fails verification)
        assertThat(result).isEmpty();

        // Should record failure for cached selector
        verify(cacheService).recordFailure(TENANT_ID, ELEMENT_DESCRIPTION, URL);
    }

    @Test
    void shouldFindElementWithoutVerification() {
        // Given - Cache hit
        CachedSelector cached = createCachedSelector("#cached-btn", 10, 2);
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.of(cached));

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElementWithoutVerification(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().selector()).isEqualTo("#cached-btn");
        assertThat(result.get().fromCache()).isTrue();
        assertThat(result.get().cacheSuccessCount()).isEqualTo(10);

        // Should NOT verify or record anything
        verifyNoInteractions(aiSelectorFinder);
        verify(cacheService, never()).recordSuccess(any(), any(), any());
        verify(cacheService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void shouldFindElementWithoutVerificationFromAi() {
        // Given - Cache miss
        when(cacheService.findCached(TENANT_ID, ELEMENT_DESCRIPTION, URL))
                .thenReturn(Optional.empty());

        // AI returns selector
        when(aiSelectorFinder.findSelector(ELEMENT_DESCRIPTION, SNAPSHOT))
                .thenReturn(Optional.of("#ai-selector"));

        // When
        Optional<SmartDriver.SelectorResult> result = smartDriver.findElementWithoutVerification(
                TENANT_ID, ELEMENT_DESCRIPTION, URL, SNAPSHOT
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().selector()).isEqualTo("#ai-selector");
        assertThat(result.get().fromCache()).isFalse();

        // Should cache immediately
        verify(cacheService).cacheSelector(
                eq(TENANT_ID), eq(ELEMENT_DESCRIPTION), eq(URL),
                eq("#ai-selector"), eq(ELEMENT_DESCRIPTION)
        );
    }

    @Test
    void shouldRecordOutcomeSuccess() {
        // When
        smartDriver.recordOutcome(TENANT_ID, ELEMENT_DESCRIPTION, URL, true);

        // Then
        verify(cacheService).recordSuccess(TENANT_ID, ELEMENT_DESCRIPTION, URL);
        verify(cacheService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void shouldRecordOutcomeFailure() {
        // When
        smartDriver.recordOutcome(TENANT_ID, ELEMENT_DESCRIPTION, URL, false);

        // Then
        verify(cacheService).recordFailure(TENANT_ID, ELEMENT_DESCRIPTION, URL);
        verify(cacheService, never()).recordSuccess(any(), any(), any());
    }

    @Test
    void shouldInvalidateSelector() {
        // When
        smartDriver.invalidateSelector(TENANT_ID, ELEMENT_DESCRIPTION, URL);

        // Then
        verify(cacheService).invalidate(TENANT_ID, ELEMENT_DESCRIPTION, URL);
    }

    private CachedSelector createCachedSelector(String selector, int successCount, int failureCount) {
        return new CachedSelector(
                UUID.randomUUID(),
                TENANT_ID,
                "hash",
                "pattern",
                selector,
                ELEMENT_DESCRIPTION,
                successCount,
                failureCount,
                Instant.now(),
                Instant.now()
        );
    }
}
