package com.ai2qa.application.orchestrator;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.application.security.PromptSanitizer;
import com.ai2qa.domain.model.DomSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ObstacleDetector.
 */
@ExtendWith(MockitoExtension.class)
class ObstacleDetectorTest {

    @Mock
    private ChatClientPort chatClient;

    @Mock
    private PromptSanitizer promptSanitizer;

    private ObstacleDetector obstacleDetector;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        ByteArrayResource systemPrompt = new ByteArrayResource("Test system prompt".getBytes());

        // Pass-through sanitizer for tests (use lenient to avoid UnnecessaryStubbingException)
        org.mockito.Mockito.lenient().when(promptSanitizer.sanitizeText(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        obstacleDetector = new ObstacleDetector(chatClient, objectMapper, promptSanitizer, systemPrompt);
    }

    @Nested
    class Detect {

        @Test
        void shouldDetectCookieConsentBanner() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div class='cookie-banner'>Accept our cookies</div>");
            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "cookie_consent",
                    "description": "Cookie consent banner",
                    "dismissSelector": "button.accept-cookies",
                    "dismissText": "Accept All",
                    "confidence": "high"
                }
                """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().obstacleType()).isEqualTo("cookie_consent");
            assertThat(result.get().dismissSelector()).isEqualTo("button.accept-cookies");
            assertThat(result.get().dismissText()).isEqualTo("Accept All");
            assertThat(result.get().confidence()).isEqualTo(ObstacleDetector.Confidence.HIGH);
        }

        @Test
        void shouldDetectLegalTermsPopup() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div class='modal'>Legal terms. Click Agree to continue.</div>");
            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "legal_agreement",
                    "description": "Legal terms and privacy popup",
                    "dismissSelector": "button[aria-label='Agree']",
                    "dismissText": "Agree",
                    "confidence": "high"
                }
                """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().obstacleType()).isEqualTo("legal_agreement");
            assertThat(result.get().dismissSelector()).isEqualTo("button[aria-label='Agree']");
        }

        @Test
        void shouldReturnEmptyWhenNoObstacle() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div>Normal page content</div>");
            String aiResponse = """
                {
                    "obstacleDetected": false,
                    "obstacleType": null,
                    "description": null,
                    "dismissSelector": null,
                    "dismissText": null,
                    "confidence": "high"
                }
                """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenSnapshotIsNull() {
            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenSnapshotContentIsBlank() {
            // Given
            DomSnapshot snapshot = new DomSnapshot("", "https://example.com", "Test", Instant.now());

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldHandleMalformedAiResponse() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div>Some content</div>");
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn("not valid json");

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldHandleAiException() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div>Some content</div>");
            when(chatClient.call(anyString(), anyString(), anyDouble()))
                    .thenThrow(new RuntimeException("AI unavailable"));

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldHandleMarkdownWrappedJson() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div class='modal'>Newsletter signup</div>");
            String aiResponse = """
                ```json
                {
                    "obstacleDetected": true,
                    "obstacleType": "newsletter_popup",
                    "description": "Newsletter subscription popup",
                    "dismissSelector": "button.close-popup",
                    "dismissText": "No thanks",
                    "confidence": "medium"
                }
                ```
                """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().obstacleType()).isEqualTo("newsletter_popup");
            assertThat(result.get().confidence()).isEqualTo(ObstacleDetector.Confidence.MEDIUM);
        }

        @Test
        void shouldReturnEmptyWhenObstacleDetectedButNoSelector() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div class='popup'>Some popup</div>");
            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "unknown_popup",
                    "description": "Unknown popup",
                    "dismissSelector": "",
                    "dismissText": null,
                    "confidence": "low"
                }
                """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldParseConfidenceLevelsCorrectly() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div>Content</div>");

            // Test LOW confidence
            String aiResponseLow = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "chat_widget",
                    "description": "Chat widget",
                    "dismissSelector": "button.minimize",
                    "dismissText": "Close",
                    "confidence": "low"
                }
                """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponseLow);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().confidence()).isEqualTo(ObstacleDetector.Confidence.LOW);
        }

        @Test
        void shouldHandleNullConfidence() {
            // Given
            DomSnapshot snapshot = createSnapshot("<div>Content</div>");
            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "popup",
                    "description": "Some popup",
                    "dismissSelector": "button.close",
                    "dismissText": "Close",
                    "confidence": null
                }
                """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().confidence()).isEqualTo(ObstacleDetector.Confidence.MEDIUM); // Default
        }
    }

    @Nested
    class ConfidenceEnum {

        @Test
        void shouldParseHighConfidence() {
            assertThat(ObstacleDetector.Confidence.fromString("high"))
                    .isEqualTo(ObstacleDetector.Confidence.HIGH);
            assertThat(ObstacleDetector.Confidence.fromString("HIGH"))
                    .isEqualTo(ObstacleDetector.Confidence.HIGH);
        }

        @Test
        void shouldParseLowConfidence() {
            assertThat(ObstacleDetector.Confidence.fromString("low"))
                    .isEqualTo(ObstacleDetector.Confidence.LOW);
            assertThat(ObstacleDetector.Confidence.fromString("LOW"))
                    .isEqualTo(ObstacleDetector.Confidence.LOW);
        }

        @Test
        void shouldDefaultToMediumForUnknown() {
            assertThat(ObstacleDetector.Confidence.fromString("unknown"))
                    .isEqualTo(ObstacleDetector.Confidence.MEDIUM);
            assertThat(ObstacleDetector.Confidence.fromString(null))
                    .isEqualTo(ObstacleDetector.Confidence.MEDIUM);
            assertThat(ObstacleDetector.Confidence.fromString(""))
                    .isEqualTo(ObstacleDetector.Confidence.MEDIUM);
        }
    }

    @Nested
    class ConsentExtraction {

        @Test
        void shouldExtractConsentContentFromLargeDom() {
            // Given: A DOM larger than 15KB with consent content at the end
            String normalContent = "x".repeat(16000); // Exceeds MAX_DOM_LENGTH (15000)
            String consentContent = "\nbutton \"Agree\" [ref=e503]\n";
            String fullDom = normalContent + consentContent;

            DomSnapshot snapshot = createSnapshot(fullDom);

            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "cookie_consent",
                    "description": "Cookie consent banner",
                    "dismissSelector": "[ref=e503]",
                    "dismissText": "Agree",
                    "confidence": "high"
                }
                """;
            when(chatClient.call(anyString(), any(), anyDouble())).thenReturn(aiResponse);

            // When
            obstacleDetector.detect(snapshot);

            // Then: Verify the prompt sent to AI contains the consent content
            org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(chatClient).call(anyString(), promptCaptor.capture(), anyDouble());

            String prompt = promptCaptor.getValue();
            assertThat(prompt).contains("CONSENT/OVERLAY CONTENT");
            assertThat(prompt).contains("Agree");
        }

        @Test
        void shouldExtractIframeContentFromLargeDom() {
            // Given: DOM with IFRAME CONTENT section at the end
            String normalContent = "y".repeat(16000);
            String iframeContent = "\nIFRAME CONTENT:\n  button \"Accept\" [ref=e100]\n";
            String fullDom = normalContent + iframeContent;

            DomSnapshot snapshot = createSnapshot(fullDom);

            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "iframe_consent",
                    "description": "Iframe consent",
                    "dismissSelector": "[ref=e100]",
                    "dismissText": "Accept",
                    "confidence": "high"
                }
                """;
            when(chatClient.call(anyString(), any(), anyDouble())).thenReturn(aiResponse);

            // When
            obstacleDetector.detect(snapshot);

            // Then
            org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(chatClient).call(anyString(), promptCaptor.capture(), anyDouble());

            String prompt = promptCaptor.getValue();
            assertThat(prompt).contains("CONSENT/OVERLAY CONTENT");
            assertThat(prompt).contains("IFRAME CONTENT");
        }

        @Test
        void shouldNotExtractWhenDomFitsWithinLimit() {
            // Given: DOM smaller than 15KB (no consent patterns to avoid confusion)
            String smallDom = "normal page content without special patterns";
            DomSnapshot snapshot = createSnapshot(smallDom);

            String aiResponse = """
                {
                    "obstacleDetected": false
                }
                """;
            when(chatClient.call(anyString(), any(), anyDouble())).thenReturn(aiResponse);

            // When
            obstacleDetector.detect(snapshot);

            // Then: Prompt should NOT contain the extraction section header
            org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(chatClient).call(anyString(), promptCaptor.capture(), anyDouble());

            String prompt = promptCaptor.getValue();
            // Check for the actual section header, not just the text mentioned in instructions
            assertThat(prompt).doesNotContain("## CONSENT/OVERLAY CONTENT (Extracted from page)");
        }

        @Test
        void shouldExtractMultipleConsentPatterns() {
            // Given: Large DOM with multiple consent-related patterns
            String normalContent = "z".repeat(16000);
            String consentContent = """

                onetrust-banner-sdk
                cookie consent dialog
                gdpr-popup visible
                button "Accept All Cookies" [ref=e200]
                """;
            String fullDom = normalContent + consentContent;

            DomSnapshot snapshot = createSnapshot(fullDom);

            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "cookie_consent",
                    "description": "OneTrust cookie banner",
                    "dismissSelector": "[ref=e200]",
                    "dismissText": "Accept All Cookies",
                    "confidence": "high"
                }
                """;
            when(chatClient.call(anyString(), any(), anyDouble())).thenReturn(aiResponse);

            // When
            obstacleDetector.detect(snapshot);

            // Then
            org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(chatClient).call(anyString(), promptCaptor.capture(), anyDouble());

            String prompt = promptCaptor.getValue();
            assertThat(prompt).contains("CONSENT/OVERLAY CONTENT");
            assertThat(prompt).contains("onetrust");
        }

        @Test
        void shouldHandleCnnStyleConsentBanner() {
            // Given: Simulating CNN's consent banner at position 107KB
            String mainContent = "main page content\n".repeat(7000); // ~112KB
            String cnnConsent = """

                IFRAME CONTENT:
                  dialog "sp_message"
                    heading "We value your privacy"
                    button "Agree" [ref=e503]
                """;
            String fullDom = mainContent + cnnConsent;

            DomSnapshot snapshot = new DomSnapshot(
                    fullDom,
                    "https://cnn.com",
                    "CNN - Breaking News",
                    Instant.now()
            );

            String aiResponse = """
                {
                    "obstacleDetected": true,
                    "obstacleType": "privacy_consent",
                    "description": "CNN privacy consent dialog",
                    "dismissSelector": "[ref=e503]",
                    "dismissText": "Agree",
                    "confidence": "high"
                }
                """;
            when(chatClient.call(anyString(), any(), anyDouble())).thenReturn(aiResponse);

            // When
            Optional<ObstacleDetector.ObstacleInfo> result = obstacleDetector.detect(snapshot);

            // Then
            assertThat(result).isPresent();

            org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(chatClient).call(anyString(), promptCaptor.capture(), anyDouble());

            String prompt = promptCaptor.getValue();
            assertThat(prompt).contains("CONSENT/OVERLAY CONTENT");
            assertThat(prompt).contains("sp_message");
        }
    }

    // ==================== Helper Methods ====================

    private DomSnapshot createSnapshot(String content) {
        return new DomSnapshot(content, "https://example.com", "Test Page", Instant.now());
    }
}
