package com.ai2qa.application.a11y;

import com.ai2qa.domain.model.TestPersona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for A11yAuditorService.
 */
@DisplayName("A11yAuditorService")
class A11yAuditorServiceTest {

    private A11yAuditorService auditorService;

    @BeforeEach
    void setUp() {
        auditorService = new A11yAuditorService();
    }

    @Nested
    @DisplayName("shouldScan")
    class ShouldScanTests {

        @Test
        @DisplayName("should scan on navigate action")
        void scansOnNavigate() {
            assertThat(auditorService.shouldScan("navigate", TestPersona.STANDARD)).isTrue();
            assertThat(auditorService.shouldScan("navigate", TestPersona.CHAOS)).isTrue();
            assertThat(auditorService.shouldScan("navigate", TestPersona.HACKER)).isTrue();
        }

        @Test
        @DisplayName("should scan for STANDARD persona on any action")
        void scansForStandardPersona() {
            assertThat(auditorService.shouldScan("click", TestPersona.STANDARD)).isTrue();
            assertThat(auditorService.shouldScan("type", TestPersona.STANDARD)).isTrue();
            assertThat(auditorService.shouldScan("wait", TestPersona.STANDARD)).isTrue();
        }

        @Test
        @DisplayName("should not scan CHAOS/HACKER on non-navigate actions")
        void doesNotScanChaosHacker() {
            assertThat(auditorService.shouldScan("click", TestPersona.CHAOS)).isFalse();
            assertThat(auditorService.shouldScan("type", TestPersona.HACKER)).isFalse();
        }
    }

    @Nested
    @DisplayName("parseAxeResults")
    class ParseAxeResultsTests {

        @Test
        @DisplayName("should return empty list for null input")
        void emptyForNull() {
            assertThat(auditorService.parseAxeResults(null)).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty violations")
        void emptyForNoViolations() {
            Map<String, Object> results = Map.of("violations", List.of());
            assertThat(auditorService.parseAxeResults(results)).isEmpty();
        }

        @Test
        @DisplayName("should parse violations correctly")
        void parsesViolations() {
            Map<String, Object> violation = Map.of(
                    "id", "button-name",
                    "impact", "critical",
                    "help", "Buttons must have discernible text",
                    "nodes", List.of(
                            Map.of("target", List.of("button#submit"))
                    )
            );
            Map<String, Object> results = Map.of("violations", List.of(violation));

            List<String> warnings = auditorService.parseAxeResults(results);

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0)).contains("CRITICAL");
            assertThat(warnings.get(0)).contains("Buttons must have discernible text");
            assertThat(warnings.get(0)).contains("button#submit");
        }

        @Test
        @DisplayName("should handle multiple violations")
        void handlesMultipleViolations() {
            List<Map<String, Object>> violations = List.of(
                    Map.of("id", "button-name", "impact", "critical", "help", "Missing button text", "nodes", List.of(Map.of("target", List.of("button#a")))),
                    Map.of("id", "color-contrast", "impact", "serious", "help", "Low contrast", "nodes", List.of(Map.of("target", List.of("p.text")))),
                    Map.of("id", "image-alt", "impact", "moderate", "help", "Missing alt", "nodes", List.of(Map.of("target", List.of("img.logo"))))
            );
            Map<String, Object> results = Map.of("violations", violations);

            List<String> warnings = auditorService.parseAxeResults(results);

            assertThat(warnings).hasSize(3);
            assertThat(warnings.get(0)).contains("CRITICAL");
            assertThat(warnings.get(1)).contains("SERIOUS");
            assertThat(warnings.get(2)).contains("MODERATE");
        }
    }

    @Nested
    @DisplayName("generateHealerContext")
    class GenerateHealerContextTests {

        @Test
        @DisplayName("should return empty for no warnings")
        void emptyForNoWarnings() {
            assertThat(auditorService.generateHealerContext(List.of())).isEmpty();
            assertThat(auditorService.generateHealerContext(null)).isEmpty();
        }

        @Test
        @DisplayName("should format warnings for Healer")
        void formatsForHealer() {
            List<String> warnings = List.of(
                    "[CRITICAL] Missing button text: button#submit",
                    "[SERIOUS] Low contrast: p.description"
            );

            String context = auditorService.generateHealerContext(warnings);

            assertThat(context).contains("[ACCESSIBILITY AUDIT RESULTS]");
            assertThat(context).contains("CRITICAL");
            assertThat(context).contains("SERIOUS");
            assertThat(context).contains("1 CRITICAL issue");
            assertThat(context).contains("1 SERIOUS issue");
        }

        @Test
        @DisplayName("should include actionable advice")
        void includesAdvice() {
            List<String> warnings = List.of("[MINOR] Some issue: element");

            String context = auditorService.generateHealerContext(warnings);

            assertThat(context).contains("ARIA attributes");
            assertThat(context).contains("focus management");
        }
    }

    @Nested
    @DisplayName("getWcagTags")
    class GetWcagTagsTests {

        @Test
        @DisplayName("should return correct tags for each level")
        void returnsTags() {
            assertThat(auditorService.getWcagTags("A")).containsExactly("wcag2a");
            assertThat(auditorService.getWcagTags("AA")).containsExactly("wcag2a", "wcag2aa");
            assertThat(auditorService.getWcagTags("AAA")).containsExactly("wcag2a", "wcag2aa", "wcag2aaa");
        }

        @Test
        @DisplayName("should default to AA level")
        void defaultsToAA() {
            assertThat(auditorService.getWcagTags("unknown")).containsExactly("wcag2a", "wcag2aa");
        }
    }
}
