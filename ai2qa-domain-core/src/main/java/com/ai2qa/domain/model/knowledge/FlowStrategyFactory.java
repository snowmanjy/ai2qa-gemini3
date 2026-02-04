package com.ai2qa.domain.model.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Factory for creating valid FlowStrategy instances.
 */
public final class FlowStrategyFactory {

    private static final int MAX_FLOW_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_STEPS = 50;

    private FlowStrategyFactory() {
    }

    /**
     * Creates a new FlowStrategy with validation.
     *
     * @param domain      Optional domain (null for generic strategies)
     * @param flowName    The flow name (e.g., "login", "checkout")
     * @param description Optional description
     * @param steps       List of flow steps
     * @param visibility  Visibility level
     * @param tenantId    Optional tenant ID
     * @return Optional containing the strategy if valid, empty otherwise
     */
    public static Optional<FlowStrategy> create(
            String domain,
            String flowName,
            String description,
            List<FlowStep> steps,
            Visibility visibility,
            String tenantId) {

        return validateFlowName(flowName)
                .flatMap(fn -> validateSteps(steps))
                .flatMap(s -> validateVisibilityTenant(visibility, tenantId))
                .map(vt -> new FlowStrategy(
                        FlowStrategyId.generate(),
                        Optional.ofNullable(domain).filter(d -> !d.isBlank()).map(String::toLowerCase),
                        sanitizeFlowName(flowName),
                        Optional.ofNullable(description).filter(d -> !d.isBlank())
                                .map(d -> truncate(d, MAX_DESCRIPTION_LENGTH)),
                        steps,
                        0,
                        0,
                        Optional.empty(),
                        visibility,
                        Optional.ofNullable(tenantId),
                        Instant.now(),
                        Instant.now(),
                        0
                ));
    }

    /**
     * Creates a simple global flow strategy.
     */
    public static Optional<FlowStrategy> createGlobal(
            String domain,
            String flowName,
            String description,
            List<FlowStep> steps) {
        return create(domain, flowName, description, steps, Visibility.GLOBAL, null);
    }

    /**
     * Reconstitutes a FlowStrategy from database storage.
     */
    public static FlowStrategy reconstitute(
            UUID id,
            String domain,
            String flowName,
            String description,
            List<FlowStep> steps,
            int successCount,
            int failureCount,
            Integer avgDurationMs,
            Visibility visibility,
            String tenantId,
            Instant createdAt,
            Instant updatedAt,
            int version) {

        return new FlowStrategy(
                FlowStrategyId.reconstitute(id),
                Optional.ofNullable(domain),
                flowName,
                Optional.ofNullable(description),
                steps,
                successCount,
                failureCount,
                Optional.ofNullable(avgDurationMs),
                visibility,
                Optional.ofNullable(tenantId),
                createdAt,
                updatedAt,
                version
        );
    }

    private static Optional<String> validateFlowName(String flowName) {
        if (flowName == null || flowName.isBlank()) {
            return Optional.empty();
        }
        if (flowName.length() > MAX_FLOW_NAME_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(sanitizeFlowName(flowName));
    }

    private static Optional<List<FlowStep>> validateSteps(List<FlowStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return Optional.empty();
        }
        if (steps.size() > MAX_STEPS) {
            return Optional.empty();
        }
        return Optional.of(steps);
    }

    private static Optional<Boolean> validateVisibilityTenant(Visibility visibility, String tenantId) {
        if (visibility == Visibility.TENANT || visibility == Visibility.PRIVATE) {
            if (tenantId == null || tenantId.isBlank()) {
                return Optional.empty();
            }
        }
        return Optional.of(true);
    }

    private static String sanitizeFlowName(String flowName) {
        return flowName.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
