package com.ai2qa.application.a11y;

import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for running accessibility (a11y) audits using Axe-Core.
 *
 * <p>Integrates with browser automation to detect WCAG violations during test execution.
 * Results are stored as warnings (not failures) unless critical.
 *
 * <p>Accessibility scans run on "navigate" actions or when persona is STANDARD.
 */
@Service
public class A11yAuditorService {

    private static final Logger log = LoggerFactory.getLogger(A11yAuditorService.class);

    /**
     * Impact levels for accessibility violations.
     */
    public enum Impact {
        CRITICAL,   // Must fix - blocks users
        SERIOUS,    // Should fix - significantly impacts users  
        MODERATE,   // Consider fixing - somewhat impacts users
        MINOR       // Nice to fix - minimal impact
    }

    /**
     * Represents a single accessibility violation.
     */
    public record Violation(
            String id,
            Impact impact,
            String help,
            String description,
            List<String> targets
    ) {
        /**
         * Formats the violation for display.
         */
        public String format() {
            String targetStr = targets.isEmpty() ? "page" : targets.get(0);
            return String.format("[%s] %s: %s", impact, help, targetStr);
        }
    }

    /**
     * Determines if an a11y scan should run for the given action and persona.
     *
     * @param action  The action type (navigate, click, etc.)
     * @param persona The test persona being used
     * @return true if a11y scan should run
     */
    public boolean shouldScan(String action, TestPersona persona) {
        // Always scan on navigation (new page loaded)
        if ("navigate".equalsIgnoreCase(action)) {
            return true;
        }

        // STANDARD persona acts as "The Auditor" - always scans
        if (persona == TestPersona.STANDARD) {
            return true;
        }

        // CHAOS and HACKER personas skip a11y to focus on their goals
        return false;
    }

    /**
     * Determines if an a11y scan should run for the given action and PersonaDefinition.
     *
     * @param action  The action type (navigate, click, etc.)
     * @param persona The persona definition being used
     * @return true if a11y scan should run
     */
    public boolean shouldScan(String action, PersonaDefinition persona) {
        // Always scan on navigation (new page loaded)
        if ("navigate".equalsIgnoreCase(action)) {
            return true;
        }

        // STANDARD persona acts as "The Auditor" - always scans
        if (TestPersona.STANDARD.name().equalsIgnoreCase(persona.name())) {
            return true;
        }

        // Other personas skip a11y to focus on their goals
        return false;
    }

    /**
     * Parses Axe-Core results into our Violation format.
     * 
     * <p>Note: In production, this would integrate with the actual Axe-Core library.
     * This method processes the raw results from the MCP browser bridge.
     *
     * @param axeResults Raw results from Axe-Core (via MCP bridge)
     * @return List of formatted warning strings
     */
    @SuppressWarnings("unchecked")
    public List<String> parseAxeResults(Map<String, Object> axeResults) {
        List<String> warnings = new ArrayList<>();
        
        if (axeResults == null || !axeResults.containsKey("violations")) {
            return warnings;
        }

        List<Map<String, Object>> violations = (List<Map<String, Object>>) axeResults.get("violations");
        
        for (Map<String, Object> violation : violations) {
            String id = (String) violation.getOrDefault("id", "unknown");
            String impactStr = (String) violation.getOrDefault("impact", "moderate");
            String help = (String) violation.getOrDefault("help", "Accessibility issue detected");
            
            Impact impact = parseImpact(impactStr);
            
            // Extract first target for brevity
            List<String> targets = extractTargets(violation);
            String targetStr = targets.isEmpty() ? "page" : targets.get(0);

            warnings.add(String.format("[%s] %s: %s", impact, help, targetStr));
            
            log.debug("A11y violation [{}]: {} - {}", id, help, targetStr);
        }

        if (!warnings.isEmpty()) {
            log.info("A11y audit found {} violation(s)", warnings.size());
        }

        return warnings;
    }

    /**
     * Parses impact string to enum.
     */
    private Impact parseImpact(String impact) {
        return switch (impact.toLowerCase()) {
            case "critical" -> Impact.CRITICAL;
            case "serious" -> Impact.SERIOUS;
            case "moderate" -> Impact.MODERATE;
            default -> Impact.MINOR;
        };
    }

    /**
     * Extracts target selectors from violation nodes.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractTargets(Map<String, Object> violation) {
        List<String> targets = new ArrayList<>();
        
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) violation.get("nodes");
        if (nodes == null) return targets;

        for (Map<String, Object> node : nodes) {
            List<String> nodeTargets = (List<String>) node.get("target");
            if (nodeTargets != null && !nodeTargets.isEmpty()) {
                targets.add(nodeTargets.get(0));
                if (targets.size() >= 3) break; // Limit to first 3 targets
            }
        }

        return targets;
    }

    /**
     * Generates context for The Healer when accessibility issues may have caused a failure.
     *
     * @param warnings Accessibility warnings from the step
     * @return Formatted context string for Healer prompt
     */
    public String generateHealerContext(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n[ACCESSIBILITY AUDIT RESULTS]\n");
        context.append("The following WCAG violations were detected on this page:\n\n");
        
        int criticalCount = 0;
        int seriousCount = 0;
        
        for (String warning : warnings) {
            context.append("• ").append(warning).append("\n");
            if (warning.contains("[CRITICAL]")) criticalCount++;
            if (warning.contains("[SERIOUS]")) seriousCount++;
        }

        context.append("\n📊 Summary:\n");
        if (criticalCount > 0) {
            context.append("• ⛔ ").append(criticalCount)
                    .append(" CRITICAL issue(s) - These may block user interaction.\n");
        }
        if (seriousCount > 0) {
            context.append("• ⚠️ ").append(seriousCount)
                    .append(" SERIOUS issue(s) - These significantly impact users.\n");
        }

        context.append("\n💡 Consider: If an element couldn't be clicked, it may be due to missing ARIA attributes or poor focus management.\n");

        return context.toString();
    }

    /**
     * Returns WCAG tags to check based on compliance level.
     */
    public List<String> getWcagTags(String level) {
        return switch (level.toUpperCase()) {
            case "A" -> List.of("wcag2a");
            case "AA" -> List.of("wcag2a", "wcag2aa");
            case "AAA" -> List.of("wcag2a", "wcag2aa", "wcag2aaa");
            default -> List.of("wcag2a", "wcag2aa"); // Default to AA
        };
    }
}
