package com.ai2qa.domain.model;

import com.ai2qa.domain.factory.ActionStepFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ActionStep record.
 */
class ActionStepTest {

    @Test
    void shouldCreateNavigateAction() {
        // When
        ActionStep step = ActionStepFactory.navigate("https://example.com");

        // Then
        assertThat(step.action()).isEqualTo("navigate");
        assertThat(step.target()).isEqualTo("https://example.com");
        assertThat(step.value()).contains("https://example.com");
        assertThat(step.selector()).isEmpty();
        assertThat(step.stepId()).startsWith("step-");
    }

    @Test
    void shouldCreateClickAction() {
        // When
        ActionStep step = ActionStepFactory.click("login button");

        // Then
        assertThat(step.action()).isEqualTo("click");
        assertThat(step.target()).isEqualTo("login button");
        assertThat(step.selector()).isEmpty();
        assertThat(step.value()).isEmpty();
    }

    @Test
    void shouldCreateClickWithSelector() {
        // When
        ActionStep step = ActionStepFactory.clickSelector("login button", "button#login");

        // Then
        assertThat(step.action()).isEqualTo("click");
        assertThat(step.target()).isEqualTo("login button");
        assertThat(step.selector()).contains("button#login");
    }

    @Test
    void shouldCreateTypeAction() {
        // When
        ActionStep step = ActionStepFactory.type("email field", "test@example.com");

        // Then
        assertThat(step.action()).isEqualTo("type");
        assertThat(step.target()).isEqualTo("email field");
        assertThat(step.value()).contains("test@example.com");
    }

    @Test
    void shouldCreateTypeIntoWithSelector() {
        // When
        ActionStep step = ActionStepFactory.typeInto("email field", "input#email", "test@example.com");

        // Then
        assertThat(step.action()).isEqualTo("type");
        assertThat(step.target()).isEqualTo("email field");
        assertThat(step.selector()).contains("input#email");
        assertThat(step.value()).contains("test@example.com");
    }

    @Test
    void shouldCreateWaitAction() {
        // When
        ActionStep step = ActionStepFactory.waitFor("element to load", 5000);

        // Then
        assertThat(step.action()).isEqualTo("wait");
        assertThat(step.target()).isEqualTo("element to load");
        assertThat(step.params()).containsEntry("timeout", "5000");
    }

    @Test
    void shouldCreateScreenshotAction() {
        // When
        ActionStep step = ActionStepFactory.screenshot("login page");

        // Then
        assertThat(step.action()).isEqualTo("screenshot");
        assertThat(step.target()).isEqualTo("login page");
    }

    @Test
    void shouldCreateWithMinimalFields() {
        // When
        ActionStep step = ActionStepFactory.of("custom", "target");

        // Then
        assertThat(step.action()).isEqualTo("custom");
        assertThat(step.target()).isEqualTo("target");
        assertThat(step.selector()).isEmpty();
        assertThat(step.value()).isEmpty();
        assertThat(step.params()).isEmpty();
    }

    @Test
    void shouldCreateWithNewSelector() {
        // Given
        ActionStep original = ActionStepFactory.click("button");

        // When
        ActionStep updated = ActionStepFactory.withSelector(original, "button.submit");

        // Then
        assertThat(updated.selector()).contains("button.submit");
        assertThat(updated.stepId()).isEqualTo(original.stepId());
        assertThat(updated.action()).isEqualTo(original.action());
        assertThat(updated.target()).isEqualTo(original.target());
    }

    @Test
    void shouldGenerateUniqueStepIds() {
        // When
        ActionStep step1 = ActionStepFactory.click("button1");
        ActionStep step2 = ActionStepFactory.click("button2");

        // Then
        assertThat(step1.stepId()).isNotEqualTo(step2.stepId());
    }
}
