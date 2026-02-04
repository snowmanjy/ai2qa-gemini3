package com.ai2qa.application.security;

import com.ai2qa.application.port.SecurityAuditRepository;
import com.ai2qa.application.port.SecurityAuditRepository.Decision;
import com.ai2qa.application.port.SecurityAuditRepository.SecurityAuditEntry;
import com.ai2qa.application.security.SecurityAuditService.AuditContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SecurityAuditService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityAuditService Tests")
class SecurityAuditServiceTest {

    private static final Instant NOW = Instant.parse("2024-01-15T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    @Mock
    private SecurityAuditRepository repository;

    private SecurityAuditService service;

    @BeforeEach
    void setUp() {
        service = new SecurityAuditService(repository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("Should log ALLOWED decision")
    void shouldLogAllowedDecision() {
        AuditContext context = AuditContext.of("tenant-123", "192.168.1.1", "https://example.com/test");

        service.logAllowed(context);

        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(repository).save(captor.capture());

        SecurityAuditEntry saved = captor.getValue();
        assertThat(saved.tenantId()).isEqualTo("tenant-123");
        assertThat(saved.clientIp()).isEqualTo("192.168.1.1");
        assertThat(saved.targetUrl()).isEqualTo("https://example.com/test");
        assertThat(saved.targetDomain()).isEqualTo("example.com");
        assertThat(saved.decision()).isEqualTo(Decision.ALLOWED);
        assertThat(saved.blockReason()).isNull();
        assertThat(saved.riskScore()).isEqualTo(0);
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Should log BLOCKED decision with reason and risk score")
    void shouldLogBlockedDecision() {
        AuditContext context = AuditContext.of("tenant-123", "192.168.1.1", "http://169.254.169.254/metadata");

        service.logBlocked(context, "Cloud metadata endpoint blocked", 100);

        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(repository).save(captor.capture());

        SecurityAuditEntry saved = captor.getValue();
        assertThat(saved.decision()).isEqualTo(Decision.BLOCKED);
        assertThat(saved.blockReason()).isEqualTo("Cloud metadata endpoint blocked");
        assertThat(saved.riskScore()).isEqualTo(100);
        assertThat(saved.targetDomain()).isEqualTo("169.254.169.254");
    }

    @Test
    @DisplayName("Should log RATE_LIMITED decision")
    void shouldLogRateLimitedDecision() {
        AuditContext context = AuditContext.of("tenant-123", "192.168.1.1", "https://example.com");

        service.logRateLimited(context, "User rate limit exceeded");

        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(repository).save(captor.capture());

        SecurityAuditEntry saved = captor.getValue();
        assertThat(saved.decision()).isEqualTo(Decision.RATE_LIMITED);
        assertThat(saved.blockReason()).isEqualTo("User rate limit exceeded");
        assertThat(saved.riskScore()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should handle repository exceptions gracefully")
    void shouldHandleRepositoryExceptions() {
        AuditContext context = AuditContext.of("tenant-123", "192.168.1.1", "https://example.com");
        doThrow(new RuntimeException("DB error")).when(repository).save(any());

        // Should not throw
        service.logAllowed(context);

        verify(repository).save(any());
    }

    @Test
    @DisplayName("Should handle invalid URLs gracefully")
    void shouldHandleInvalidUrls() {
        AuditContext context = AuditContext.of("tenant-123", "192.168.1.1", "not-a-valid-url");

        service.logAllowed(context);

        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(repository).save(captor.capture());

        SecurityAuditEntry saved = captor.getValue();
        assertThat(saved.targetUrl()).isEqualTo("not-a-valid-url");
        assertThat(saved.targetDomain()).isNull();
    }

    @Test
    @DisplayName("Should include user agent and request ID when provided")
    void shouldIncludeOptionalFields() {
        AuditContext context = AuditContext.of("tenant-123", "192.168.1.1", "https://example.com")
                .withUserAgent("Mozilla/5.0")
                .withRequestId("req-12345");

        service.logAllowed(context);

        ArgumentCaptor<SecurityAuditEntry> captor = ArgumentCaptor.forClass(SecurityAuditEntry.class);
        verify(repository).save(captor.capture());

        SecurityAuditEntry saved = captor.getValue();
        assertThat(saved.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.requestId()).isEqualTo("req-12345");
    }

    @Test
    @DisplayName("Should get security stats from repository")
    void shouldGetSecurityStats() {
        Instant since = NOW.minusSeconds(3600);
        when(repository.countByDecisionSince(Decision.BLOCKED, since)).thenReturn(10L);
        when(repository.countByDecisionSince(Decision.RATE_LIMITED, since)).thenReturn(5L);

        SecurityAuditService.SecurityStats stats = service.getStats(since);

        assertThat(stats.blockedCount()).isEqualTo(10);
        assertThat(stats.rateLimitedCount()).isEqualTo(5);
    }
}
