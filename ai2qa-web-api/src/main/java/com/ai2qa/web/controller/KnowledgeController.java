package com.ai2qa.web.controller;

import com.ai2qa.application.knowledge.KnowledgeAccessGuard;
import com.ai2qa.application.knowledge.KnowledgeService;
import com.ai2qa.domain.model.knowledge.PatternType;
import com.ai2qa.domain.model.knowledge.SelectorAlternative;
import com.ai2qa.domain.model.knowledge.SelectorType;
import com.ai2qa.domain.model.knowledge.SitePattern;
import com.ai2qa.domain.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API for QA knowledge operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/knowledge/domain/{domain} - Fetch patterns for a domain (RENT)</li>
 *   <li>POST /api/v1/knowledge/learn - Submit learnings from test execution (COLLECT)</li>
 *   <li>GET /api/v1/knowledge/stats - Get contribution statistics</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);
    private static final BigDecimal CREDIT_COST_PER_DOMAIN = new BigDecimal("0.5");

    private final KnowledgeService knowledgeService;
    private final KnowledgeAccessGuard accessGuard;

    public KnowledgeController(
            KnowledgeService knowledgeService,
            KnowledgeAccessGuard accessGuard) {
        this.knowledgeService = knowledgeService;
        this.accessGuard = accessGuard;
    }

    /**
     * Fetches QA patterns for a domain.
     *
     * <p>This is the RENT feature - allows local agents and AI tools
     * to fetch accumulated knowledge before testing a site.
     */
    @GetMapping("/domain/{domain}")
    public ResponseEntity<KnowledgeResponse> getKnowledge(
            @PathVariable("domain") String domain,
            @RequestParam(value = "flowName", required = false) String flowName,
            @RequestParam(value = "includeSelectors", defaultValue = "true") boolean includeSelectors,
            @RequestParam(value = "includeQuirks", defaultValue = "true") boolean includeQuirks) {

        String tenantId = TenantContext.getTenantId();

        // Check access limits
        KnowledgeAccessGuard.AccessResult accessResult = accessGuard.checkAccess(tenantId, domain);
        if (!accessResult.isAllowed()) {
            log.warn("[KNOWLEDGE] Access denied for tenant {}: {}", tenantId, accessResult.reason());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new KnowledgeResponse(
                            domain, null, List.of(), List.of(),
                            BigDecimal.ZERO, accessResult.reason()
                    ));
        }

        // Fetch patterns for domain
        List<SitePattern> patterns = knowledgeService.findPatterns(domain, tenantId);

        // Filter patterns based on request
        List<PatternDto> patternDtos = patterns.stream()
                .filter(p -> {
                    if (!includeSelectors && p.type() == PatternType.SELECTOR) return false;
                    if (!includeQuirks && p.type() == PatternType.QUIRK) return false;
                    return true;
                })
                .map(this::toPatternDto)
                .toList();

        // Extract quirks as separate list
        List<String> quirks = patterns.stream()
                .filter(p -> p.type() == PatternType.QUIRK && includeQuirks)
                .map(SitePattern::value)
                .toList();

        // Detect framework from patterns (simplified)
        String framework = detectFramework(patterns);

        // Log access for metering (currently free, charging can be added later)
        BigDecimal creditsCharged = BigDecimal.ZERO; // Free during beta
        accessGuard.logAccess(tenantId, domain, patternDtos.size(), creditsCharged);

        log.info("[KNOWLEDGE] Tenant {} fetched {} patterns for domain {}",
                tenantId, patternDtos.size(), domain);

        return ResponseEntity.ok(new KnowledgeResponse(
                domain,
                framework,
                patternDtos,
                quirks,
                creditsCharged,
                null
        ));
    }

    /**
     * Submits learnings from a test execution.
     *
     * <p>Used by local agents and cloud runs to contribute patterns
     * back to the knowledge base.
     */
    @PostMapping("/learn")
    public ResponseEntity<LearnResponse> submitLearnings(
            @RequestBody LearnRequest request) {

        String tenantId = TenantContext.getTenantId();

        if (request.domain() == null || request.domain().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new LearnResponse(0, 0, "Domain is required"));
        }

        int patternsStored = 0;
        int patternsUpdated = 0;

        // Process pattern results (update success/failure stats)
        if (request.results() != null) {
            for (PatternResult result : request.results()) {
                try {
                    Optional<SitePattern> existing = knowledgeService.findPatterns(
                            request.domain(), tenantId).stream()
                            .filter(p -> p.key().equals(result.patternKey()))
                            .findFirst();

                    if (existing.isPresent()) {
                        if (result.success()) {
                            knowledgeService.recordPatternSuccess(
                                    existing.get().id(),
                                    result.durationMs() != null ? result.durationMs() : 0
                            );
                        } else {
                            knowledgeService.recordPatternFailure(existing.get().id());
                        }
                        patternsUpdated++;
                    }
                } catch (Exception e) {
                    log.warn("[KNOWLEDGE] Failed to update pattern stats: {}", e.getMessage());
                }
            }
        }

        // Store new patterns discovered
        if (request.newPatterns() != null) {
            for (NewPattern pattern : request.newPatterns()) {
                try {
                    PatternType type = parsePatternType(pattern.patternType());
                    String value = pattern.selector() != null ? pattern.selector() : pattern.value();

                    Optional<SitePattern> stored = knowledgeService.storePattern(
                            request.domain(),
                            type,
                            pattern.key(),
                            value,
                            request.runId(),
                            tenantId
                    );

                    if (stored.isPresent()) {
                        patternsStored++;
                        log.debug("[KNOWLEDGE] Stored pattern from {}: {}/{}",
                                tenantId, type, pattern.key());
                    }
                } catch (Exception e) {
                    log.warn("[KNOWLEDGE] Failed to store pattern: {}", e.getMessage());
                }
            }
        }

        log.info("[KNOWLEDGE] Tenant {} learned: {} new patterns, {} stats updated for {}",
                tenantId, patternsStored, patternsUpdated, request.domain());

        return ResponseEntity.ok(new LearnResponse(
                patternsStored,
                patternsUpdated,
                "Learnings recorded successfully"
        ));
    }

    /**
     * Gets knowledge contribution statistics for the tenant.
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        String tenantId = TenantContext.getTenantId();

        KnowledgeService.KnowledgeStats stats = knowledgeService.getStats(tenantId);

        return ResponseEntity.ok(new StatsResponse(
                stats.patternsContributed(),
                stats.patternsAccessed(),
                stats.domainsCovered()
        ));
    }

    // ==================== Helper Methods ====================

    private PatternType parsePatternType(String type) {
        if (type == null) return PatternType.SELECTOR;
        return switch (type.toUpperCase()) {
            case "TIMING" -> PatternType.TIMING;
            case "AUTH" -> PatternType.AUTH;
            case "QUIRK" -> PatternType.QUIRK;
            default -> PatternType.SELECTOR;
        };
    }

    private PatternDto toPatternDto(SitePattern pattern) {
        List<SelectorAlternative> alternatives = knowledgeService.findAlternatives(pattern.id());
        List<AlternativeSelectorDto> altDtos = alternatives.stream()
                .map(alt -> new AlternativeSelectorDto(
                        alt.value(),
                        alt.type().name(),
                        alt.successRate()
                ))
                .toList();

        return new PatternDto(
                pattern.key(),
                pattern.value(),
                pattern.type().name(),
                pattern.successRate(),
                pattern.avgDurationMs().orElse(null),
                altDtos
        );
    }

    private String detectFramework(List<SitePattern> patterns) {
        // Simple framework detection based on pattern values
        for (SitePattern pattern : patterns) {
            String value = pattern.value().toLowerCase();
            if (value.contains("react") || value.contains("data-reactid") ||
                    value.contains("__next") || value.contains("data-hydrated")) {
                return "react";
            }
            if (value.contains("ng-") || value.contains("_ngcontent") ||
                    value.contains("angular")) {
                return "angular";
            }
            if (value.contains("v-") || value.contains("data-v-") ||
                    value.contains("vue")) {
                return "vue";
            }
        }
        return null;
    }

    // ==================== Request/Response DTOs ====================

    /**
     * Request to submit learnings from test execution.
     */
    public record LearnRequest(
            String domain,
            UUID runId,
            List<PatternResult> results,
            List<NewPattern> newPatterns
    ) {}

    /**
     * Result of using a pattern during test execution.
     */
    public record PatternResult(
            String patternKey,
            String selectorUsed,
            boolean success,
            Integer durationMs,
            String errorMessage
    ) {}

    /**
     * A new pattern discovered during test execution.
     */
    public record NewPattern(
            String key,
            String selector,
            String selectorType,
            String patternType,
            String value,
            String description
    ) {}

    /**
     * Response after submitting learnings.
     */
    public record LearnResponse(
            int patternsStored,
            int patternsUpdated,
            String message
    ) {}

    /**
     * Knowledge statistics response.
     */
    public record StatsResponse(
            long patternsContributed,
            long patternsAccessed,
            long domainsCovered
    ) {}

    // ==================== RENT Feature DTOs ====================

    /**
     * Response for knowledge fetch (RENT) request.
     */
    public record KnowledgeResponse(
            String domain,
            String framework,
            List<PatternDto> patterns,
            List<String> quirks,
            BigDecimal creditsCharged,
            String error
    ) {}

    /**
     * Pattern data for knowledge response.
     */
    public record PatternDto(
            String key,
            String selector,
            String patternType,
            BigDecimal successRate,
            Integer avgDurationMs,
            List<AlternativeSelectorDto> alternatives
    ) {}

    /**
     * Alternative selector data.
     */
    public record AlternativeSelectorDto(
            String selector,
            String selectorType,
            BigDecimal successRate
    ) {}
}
