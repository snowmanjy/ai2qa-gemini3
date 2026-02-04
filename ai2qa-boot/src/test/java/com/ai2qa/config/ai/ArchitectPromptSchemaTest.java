package com.ai2qa.config.ai;

import com.ai2qa.application.ai.PromptTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to ensure ARCHITECT_JSON_SCHEMA is consistent between:
 * 1. Embedded PromptTemplates.java
 * 2. Classpath file prompts/architect/json-schema.md
 *
 * <p>These tests prevent regressions where the schema format doesn't match
 * what GeminiClient.parseStepsResponse() expects (plain JSON array).
 */
@DisplayName("Architect Prompt Schema Consistency")
class ArchitectPromptSchemaTest {

    @Nested
    @DisplayName("JSON Schema Format Validation")
    class JsonSchemaFormatValidation {

        @Test
        @DisplayName("embedded schema specifies JSON array format")
        void embeddedSchemaSpecifiesJsonArrayFormat() {
            String schema = PromptTemplates.ARCHITECT_JSON_SCHEMA;

            // Must specify JSON array output
            assertTrue(schema.contains("JSON array"),
                    "Schema must specify JSON array format");
            assertTrue(schema.contains("[") && schema.contains("]"),
                    "Schema must show array example with brackets");

            // Schema should WARN against testPlan format (mentions it as WRONG)
            assertTrue(schema.contains("testPlan") && schema.contains("WRONG"),
                    "Schema should warn that testPlan format is WRONG");
            assertFalse(schema.contains("stepNumber"),
                    "Schema must NOT use stepNumber field - wrong format");

            // Must use correct field names
            assertTrue(schema.contains("\"action\""),
                    "Schema must specify 'action' field");
            assertTrue(schema.contains("\"target\""),
                    "Schema must specify 'target' field");
        }

        @Test
        @DisplayName("embedded schema uses lowercase action types")
        void embeddedSchemaUsesLowercaseActionTypes() {
            String schema = PromptTemplates.ARCHITECT_JSON_SCHEMA;

            // Must use lowercase action types that match parser expectations
            assertTrue(schema.contains("\"navigate\""),
                    "Schema must use lowercase 'navigate'");
            assertTrue(schema.contains("\"click\""),
                    "Schema must use lowercase 'click'");
            assertTrue(schema.contains("\"type\""),
                    "Schema must use lowercase 'type'");
            assertTrue(schema.contains("\"wait\""),
                    "Schema must use lowercase 'wait'");
            assertTrue(schema.contains("\"screenshot\""),
                    "Schema must use lowercase 'screenshot'");

            // Must NOT use uppercase action types (old format)
            assertFalse(schema.contains("NAVIGATE |"),
                    "Schema must NOT use uppercase NAVIGATE");
            assertFalse(schema.contains("| CLICK |"),
                    "Schema must NOT use uppercase CLICK");
        }

        @Test
        @DisplayName("classpath schema file matches embedded schema format")
        void classpathSchemaFileMatchesEmbeddedSchemaFormat() throws IOException {
            ClassPathResource resource = new ClassPathResource("prompts/architect/json-schema.md");
            assertTrue(resource.exists(), "Classpath prompt file must exist");

            String classpathSchema = new String(
                    resource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            // Same validation as embedded
            assertTrue(classpathSchema.contains("JSON array"),
                    "Classpath schema must specify JSON array format");
            // Schema now MENTIONS testPlan as a WRONG example - it should warn against it
            assertTrue(classpathSchema.contains("testPlan") && classpathSchema.contains("WRONG"),
                    "Classpath schema should warn that testPlan format is WRONG");
            assertFalse(classpathSchema.contains("stepNumber"),
                    "Classpath schema must NOT use stepNumber field");

            // Must use lowercase action types
            assertTrue(classpathSchema.contains("\"navigate\""),
                    "Classpath schema must use lowercase 'navigate'");
            assertTrue(classpathSchema.contains("\"click\""),
                    "Classpath schema must use lowercase 'click'");
        }
    }

    @Nested
    @DisplayName("Schema Content Validation")
    class SchemaContentValidation {

        @Test
        @DisplayName("schema instructs AI to output only JSON array")
        void schemaInstructsAiToOutputOnlyJsonArray() {
            String schema = PromptTemplates.ARCHITECT_JSON_SCHEMA;

            // Should have clear instructions about output format
            assertTrue(schema.toLowerCase().contains("only") &&
                       schema.toLowerCase().contains("json array"),
                    "Schema should explicitly say 'only JSON array'");
        }

        @Test
        @DisplayName("schema includes multi-step guidance")
        void schemaIncludesMultiStepGuidance() {
            String schema = PromptTemplates.ARCHITECT_JSON_SCHEMA;

            // Should encourage comprehensive multi-step plans
            assertTrue(schema.contains("5-15") || schema.contains("multiple"),
                    "Schema should encourage comprehensive multi-step plans");
            assertTrue(schema.contains("atomic"),
                    "Schema should mention atomic steps");
        }

        @Test
        @DisplayName("schema example shows valid JSON structure")
        void schemaExampleShowsValidJsonStructure() {
            String schema = PromptTemplates.ARCHITECT_JSON_SCHEMA;

            // The example should start with [ and end with ]
            int firstBracket = schema.indexOf('[');
            int lastBracket = schema.lastIndexOf(']');

            assertTrue(firstBracket > 0, "Schema must contain opening bracket");
            assertTrue(lastBracket > firstBracket, "Schema must contain closing bracket after opening");

            // Extract example and verify it's valid JSON structure
            String example = schema.substring(firstBracket, lastBracket + 1);
            assertTrue(example.contains("{") && example.contains("}"),
                    "Example must contain objects inside array");
        }

        @Test
        @DisplayName("schema warns against wrong formats")
        void schemaWarnsAgainstWrongFormats() {
            String schema = PromptTemplates.ARCHITECT_JSON_SCHEMA;

            // Schema should warn about common wrong formats AI produces
            assertTrue(schema.contains("WRONG"),
                    "Schema should warn about WRONG formats");
            assertTrue(schema.contains("testPlan") || schema.contains("testSteps"),
                    "Schema should mention the wrong formats AI tends to produce");
            assertTrue(schema.contains("CRITICAL") || schema.contains("PARSING FAILURE"),
                    "Schema should emphasize the critical nature of format compliance");
        }

        @Test
        @DisplayName("schema has emphatic format requirements")
        void schemaHasEmphaticFormatRequirements() {
            String schema = PromptTemplates.ARCHITECT_JSON_SCHEMA;

            // Schema should be emphatic about format (to overcome AI tendencies)
            assertTrue(schema.contains("MUST") || schema.contains("ONLY"),
                    "Schema should use emphatic language like MUST/ONLY");
            // Check for "Start" with bracket instruction - various phrasings accepted
            assertTrue(schema.contains("Start your response with [") ||
                       schema.contains("Start with [") ||
                       schema.contains("start with ["),
                    "Schema should tell AI to start with bracket");
        }
    }
}
