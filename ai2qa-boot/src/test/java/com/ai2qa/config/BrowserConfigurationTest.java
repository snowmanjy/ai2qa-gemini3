package com.ai2qa.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BrowserConfiguration.
 * Tests the kill-switch logic and effective mode determination.
 */
@DisplayName("BrowserConfiguration")
class BrowserConfigurationTest {

    @Nested
    @DisplayName("getEffectiveSnapshotMode")
    class GetEffectiveSnapshotModeTests {

        @Test
        @DisplayName("should return legacy when aria is disabled (kill-switch)")
        void returnsLegacyWhenAriaDisabled() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");
            config.setSnapshotMode("aria");
            config.setAriaEnabled(false); // kill-switch

            assertThat(config.getEffectiveSnapshotMode()).isEqualTo("legacy");
        }

        @Test
        @DisplayName("should return aria when mode is auto and engine is playwright")
        void returnsAriaForAutoModeWithPlaywright() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");
            config.setSnapshotMode("auto");
            config.setAriaEnabled(true);

            assertThat(config.getEffectiveSnapshotMode()).isEqualTo("aria");
        }

        @Test
        @DisplayName("should return legacy when mode is auto and engine is puppeteer")
        void returnsLegacyForAutoModeWithPuppeteer() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("puppeteer");
            config.setSnapshotMode("auto");
            config.setAriaEnabled(true);

            assertThat(config.getEffectiveSnapshotMode()).isEqualTo("legacy");
        }

        @Test
        @DisplayName("should return legacy when aria mode requested but engine is puppeteer")
        void returnsLegacyForAriaModeWithPuppeteer() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("puppeteer");
            config.setSnapshotMode("aria");
            config.setAriaEnabled(true);

            assertThat(config.getEffectiveSnapshotMode()).isEqualTo("legacy");
        }

        @Test
        @DisplayName("should return aria when explicitly set with playwright")
        void returnsAriaWhenExplicitlySetWithPlaywright() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");
            config.setSnapshotMode("aria");
            config.setAriaEnabled(true);

            assertThat(config.getEffectiveSnapshotMode()).isEqualTo("aria");
        }

        @Test
        @DisplayName("should return legacy when explicitly set")
        void returnsLegacyWhenExplicitlySet() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");
            config.setSnapshotMode("legacy");
            config.setAriaEnabled(true);

            assertThat(config.getEffectiveSnapshotMode()).isEqualTo("legacy");
        }
    }

    @Nested
    @DisplayName("isAriaEffectivelyEnabled")
    class IsAriaEffectivelyEnabledTests {

        @Test
        @DisplayName("should return true when effective mode is aria")
        void returnsTrueWhenEffectiveModeIsAria() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");
            config.setSnapshotMode("aria");
            config.setAriaEnabled(true);

            assertThat(config.isAriaEffectivelyEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return false when kill-switch is activated")
        void returnsFalseWhenKillSwitchActivated() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");
            config.setSnapshotMode("aria");
            config.setAriaEnabled(false);

            assertThat(config.isAriaEffectivelyEnabled()).isFalse();
        }

        @Test
        @DisplayName("should return false when engine is puppeteer")
        void returnsFalseWhenEnginePuppeteer() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("puppeteer");
            config.setSnapshotMode("auto");
            config.setAriaEnabled(true);

            assertThat(config.isAriaEffectivelyEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("isPlaywrightEngine")
    class IsPlaywrightEngineTests {

        @Test
        @DisplayName("should return true for playwright engine")
        void returnsTrueForPlaywright() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");

            assertThat(config.isPlaywrightEngine()).isTrue();
        }

        @Test
        @DisplayName("should return true for Playwright engine (case insensitive)")
        void returnsTrueForPlaywrightCaseInsensitive() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("Playwright");

            assertThat(config.isPlaywrightEngine()).isTrue();
        }

        @Test
        @DisplayName("should return false for puppeteer engine")
        void returnsFalseForPuppeteer() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("puppeteer");

            assertThat(config.isPlaywrightEngine()).isFalse();
        }
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValuesTests {

        @Test
        @DisplayName("should have puppeteer as default engine")
        void defaultEnginePuppeteer() {
            BrowserConfiguration config = new BrowserConfiguration();

            assertThat(config.getEngine()).isEqualTo("puppeteer");
        }

        @Test
        @DisplayName("should have auto as default snapshot mode")
        void defaultSnapshotModeAuto() {
            BrowserConfiguration config = new BrowserConfiguration();

            assertThat(config.getSnapshotMode()).isEqualTo("auto");
        }

        @Test
        @DisplayName("should have aria enabled by default")
        void defaultAriaEnabled() {
            BrowserConfiguration config = new BrowserConfiguration();

            assertThat(config.isAriaEnabled()).isTrue();
        }

        @Test
        @DisplayName("should have fallback enabled by default")
        void defaultFallbackEnabled() {
            BrowserConfiguration config = new BrowserConfiguration();

            assertThat(config.isFallbackEnabled()).isTrue();
        }

        @Test
        @DisplayName("default config should resolve to legacy mode (puppeteer + auto)")
        void defaultConfigResolvesToLegacy() {
            BrowserConfiguration config = new BrowserConfiguration();

            assertThat(config.getEffectiveSnapshotMode()).isEqualTo("legacy");
            assertThat(config.isAriaEffectivelyEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should include all relevant fields")
        void includesAllFields() {
            BrowserConfiguration config = new BrowserConfiguration();
            config.setEngine("playwright");
            config.setSnapshotMode("aria");
            config.setAriaEnabled(true);
            config.setFallbackEnabled(false);

            String result = config.toString();

            assertThat(result).contains("playwright");
            assertThat(result).contains("aria");
            assertThat(result).contains("ariaEnabled=true");
            assertThat(result).contains("fallbackEnabled=false");
        }
    }
}
