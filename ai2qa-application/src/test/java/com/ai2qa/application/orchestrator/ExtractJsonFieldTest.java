package com.ai2qa.application.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JSON parsing helper method in AgentOrchestrator.
 * Uses reflection to test the private extractJsonField method.
 */
class ExtractJsonFieldTest {

    private String extractJsonField(String json, String fieldName) throws Exception {
        // Use reflection to access the private method
        AgentOrchestrator orchestrator = createMinimalOrchestrator();
        Method method = AgentOrchestrator.class.getDeclaredMethod("extractJsonField", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(orchestrator, json, fieldName);
    }

    private AgentOrchestrator createMinimalOrchestrator() {
        // Create with null dependencies - we only need the method
        return new AgentOrchestrator(null, null, null, null, null, null, null, null, null, null, null, null,
                new TestOrchestratorConfig(), null, null, null);
    }

    @Nested
    @DisplayName("Basic Field Extraction")
    class BasicExtraction {

        @Test
        @DisplayName("Should extract simple string field")
        void extractSimpleField() throws Exception {
            String json = "{\"content\":\"hello world\",\"url\":\"http://example.com\"}";
            assertThat(extractJsonField(json, "content")).isEqualTo("hello world");
            assertThat(extractJsonField(json, "url")).isEqualTo("http://example.com");
        }

        @Test
        @DisplayName("Should extract field with spaces around colon")
        void extractFieldWithSpaces() throws Exception {
            String json = "{\"content\" : \"hello world\"}";
            assertThat(extractJsonField(json, "content")).isEqualTo("hello world");
        }

        @Test
        @DisplayName("Should return empty string for missing field")
        void returnEmptyForMissingField() throws Exception {
            String json = "{\"content\":\"hello\"}";
            assertThat(extractJsonField(json, "missing")).isEmpty();
        }

        @Test
        @DisplayName("Should handle null json")
        void handleNullJson() throws Exception {
            assertThat(extractJsonField(null, "content")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Escape Sequence Handling")
    class EscapeSequences {

        @Test
        @DisplayName("Should handle escaped newlines")
        void handleEscapedNewlines() throws Exception {
            String json = "{\"content\":\"line1\\nline2\\nline3\"}";
            assertThat(extractJsonField(json, "content")).isEqualTo("line1\nline2\nline3");
        }

        @Test
        @DisplayName("Should handle escaped tabs")
        void handleEscapedTabs() throws Exception {
            String json = "{\"content\":\"col1\\tcol2\\tcol3\"}";
            assertThat(extractJsonField(json, "content")).isEqualTo("col1\tcol2\tcol3");
        }

        @Test
        @DisplayName("Should handle escaped quotes")
        void handleEscapedQuotes() throws Exception {
            String json = "{\"content\":\"say \\\"hello\\\"\"}";
            assertThat(extractJsonField(json, "content")).isEqualTo("say \"hello\"");
        }

        @Test
        @DisplayName("Should handle escaped backslashes")
        void handleEscapedBackslashes() throws Exception {
            String json = "{\"content\":\"path\\\\to\\\\file\"}";
            assertThat(extractJsonField(json, "content")).isEqualTo("path\\to\\file");
        }

        @Test
        @DisplayName("Should handle mixed escape sequences")
        void handleMixedEscapes() throws Exception {
            String json = "{\"content\":\"line1\\nline2\\ttab\\\"quote\\\"\"}";
            assertThat(extractJsonField(json, "content")).isEqualTo("line1\nline2\ttab\"quote\"");
        }
    }

    @Nested
    @DisplayName("Complex Content")
    class ComplexContent {

        @Test
        @DisplayName("Should extract accessibility tree content with UIDs")
        void extractAccessibilityTree() throws Exception {
            String treeContent = "[e0] document\\n  [e1] button \\\"Login\\\"\\n  [e2] input [focused]";
            String json = "{\"content\":\"" + treeContent + "\",\"url\":\"https://example.com\",\"title\":\"Test\"}";

            String result = extractJsonField(json, "content");
            assertThat(result).contains("[e0] document");
            assertThat(result).contains("[e1] button");
        }

        @Test
        @DisplayName("Should extract URL field correctly")
        void extractUrlField() throws Exception {
            String json = "{\"content\":\"tree\",\"url\":\"https://example.com/path?query=value\",\"title\":\"Page\"}";
            assertThat(extractJsonField(json, "url")).isEqualTo("https://example.com/path?query=value");
        }

        @Test
        @DisplayName("Should extract title with special characters")
        void extractTitleWithSpecialChars() throws Exception {
            String json = "{\"content\":\"tree\",\"url\":\"https://example.com\",\"title\":\"Welcome - Page (v2)\"}";
            assertThat(extractJsonField(json, "title")).isEqualTo("Welcome - Page (v2)");
        }
    }
}
