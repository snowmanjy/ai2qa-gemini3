package com.ai2qa.application.security;

import com.ai2qa.application.port.SecurityAuditRepository;
import com.ai2qa.application.port.SecurityAuditRepository.Decision;
import com.ai2qa.application.port.SecurityAuditRepository.SecurityAuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Service for logging security audit events.
 *
 * <p>Logs all URL validation decisions for security monitoring:
 * - ALLOWED: Request passed all security checks
 * - BLOCKED: Request blocked due to blacklist, SSRF protection, etc.
 * - RATE_LIMITED: Request blocked due to rate limits
 *
 * <p>Logs are written asynchronously to avoid impacting request latency.
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditRepository repository;
    private final Clock clock;

    public SecurityAuditService(SecurityAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Logs an ALLOWED decision.
     */
    @Async
    public void logAllowed(AuditContext context) {
        log(context, Decision.ALLOWED, null, 0);
    }

    /**
     * Logs a BLOCKED decision.
     */
    @Async
    public void logBlocked(AuditContext context, String reason, int riskScore) {
        log(context, Decision.BLOCKED, reason, riskScore);
        log.warn("SECURITY BLOCKED: tenant={}, ip={}, url={}, reason={}",
                context.tenantId(), context.clientIp(), context.targetUrl(), reason);
    }

    /**
     * Logs a RATE_LIMITED decision.
     */
    @Async
    public void logRateLimited(AuditContext context, String reason) {
        log(context, Decision.RATE_LIMITED, reason, 50);
        log.warn("SECURITY RATE_LIMITED: tenant={}, ip={}, reason={}",
                context.tenantId(), context.clientIp(), reason);
    }

    private void log(AuditContext context, Decision decision, String reason, int riskScore) {
        try {
            SecurityAuditEntry entry = new SecurityAuditEntry(
                    context.tenantId(),
                    context.clientIp(),
                    context.targetUrl(),
                    extractDomain(context.targetUrl()).orElse(null),
                    decision,
                    reason,
                    riskScore,
                    context.userAgent(),
                    context.requestId(),
                    clock.instant()
            );
            repository.save(entry);
        } catch (Exception e) {
            // Don't fail requests due to audit logging errors
            log.error("Failed to write security audit log: {}", e.getMessage());
        }
    }

    private Optional<String> extractDomain(String url) {
        if (url == null) {
            return Optional.empty();
        }
        try {
            java.net.URL parsed = new java.net.URL(url);
            return Optional.of(parsed.getHost().toLowerCase());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets statistics for security monitoring dashboard.
     */
    public SecurityStats getStats(Instant since) {
        long blocked = repository.countByDecisionSince(Decision.BLOCKED, since);
        long rateLimited = repository.countByDecisionSince(Decision.RATE_LIMITED, since);
        return new SecurityStats(blocked, rateLimited);
    }

    /**
     * Context for audit logging.
     */
    public record AuditContext(
            String tenantId,
            String clientIp,
            String targetUrl,
            String userAgent,
            String requestId
    ) {
        public static AuditContext of(String tenantId, String clientIp, String targetUrl) {
            return new AuditContext(tenantId, clientIp, targetUrl, null, null);
        }

        public AuditContext withUserAgent(String userAgent) {
            return new AuditContext(tenantId, clientIp, targetUrl, userAgent, requestId);
        }

        public AuditContext withRequestId(String requestId) {
            return new AuditContext(tenantId, clientIp, targetUrl, userAgent, requestId);
        }
    }

    /**
     * Security statistics for monitoring.
     */
    public record SecurityStats(
            long blockedCount,
            long rateLimitedCount
    ) {}
}
