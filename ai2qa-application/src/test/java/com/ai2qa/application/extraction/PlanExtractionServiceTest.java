package com.ai2qa.application.extraction;

import com.ai2qa.application.port.ChatClientPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanExtractionService Tests")
class PlanExtractionServiceTest {

    @Mock
    private ChatClientPort chatClient;

    private ObjectMapper objectMapper;
    private PlanExtractionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new PlanExtractionService(chatClient, objectMapper);
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null file")
        void rejectsNullFile() {
            assertThatThrownBy(() -> service.extractGoals(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File is required");
        }

        @Test
        @DisplayName("Should reject empty file")
        void rejectsEmptyFile() {
            PlanFile emptyFile = new PlanFile(
                    "test.txt", "text/plain", new byte[0]);

            assertThatThrownBy(() -> service.extractGoals(emptyFile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File is required");
        }

        @Test
        @DisplayName("Should reject file exceeding 2MB")
        void rejectsLargeFile() {
            byte[] largeContent = new byte[3 * 1024 * 1024]; // 3MB
            PlanFile largeFile = new PlanFile(
                    "test.txt", "text/plain", largeContent);

            assertThatThrownBy(() -> service.extractGoals(largeFile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("File size exceeds 2MB limit");
        }

        @Test
        @DisplayName("Should reject unsupported file type")
        void rejectsUnsupportedType() {
            PlanFile unsupportedFile = new PlanFile(
                    "test.exe", "application/octet-stream", "content".getBytes());

            assertThatThrownBy(() -> service.extractGoals(unsupportedFile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
        }
    }

    @Nested
    @DisplayName("Text Extraction Tests")
    class TextExtractionTests {

        @Test
        @DisplayName("Should extract plain text file")
        void extractsPlainText() throws IOException {
            String content = "Goal 1: Test login\nGoal 2: Test logout";
            PlanFile txtFile = new PlanFile(
                    "test.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

            setupAiMock("[\"Test login\", \"Test logout\"]");

            List<String> goals = service.extractGoals(txtFile);

            assertThat(goals).containsExactly("Test login", "Test logout");
        }

        @Test
        @DisplayName("Should extract CSV file with space-joined columns")
        void extractsCsv() throws IOException {
            String csvContent = "ID,Step,Expected\n1,Click Login,Success\n2,Enter Password,Field visible";
            PlanFile csvFile = new PlanFile(
                    "test.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            setupAiMock("[\"Click Login Success\", \"Enter Password Field visible\"]");

            List<String> goals = service.extractGoals(csvFile);

            assertThat(goals).hasSize(2);
            verify(chatClient).call(any(String.class), any(String.class));
        }

        @Test
        @DisplayName("Should truncate text exceeding 15000 chars")
        void truncatesLongText() throws IOException {
            // Create content longer than 15000 chars
            String longContent = "A".repeat(20000);
            PlanFile txtFile = new PlanFile(
                    "test.txt", "text/plain", longContent.getBytes(StandardCharsets.UTF_8));

            setupAiMock("[\"Goal from truncated content\"]");

            List<String> goals = service.extractGoals(txtFile);

            assertThat(goals).hasSize(1);
            // Verify AI was called with truncated content
            verify(chatClient).call(any(String.class), argThat(
                    (String s) -> s.length() <= 15100 && s.contains("[Content truncated")));
        }
    }

    @Nested
    @DisplayName("AI Refinement Tests")
    class AiRefinementTests {

        @Test
        @DisplayName("Should parse JSON array from AI response")
        void parsesJsonArray() throws IOException {
            PlanFile txtFile = new PlanFile(
                    "test.txt", "text/plain", "raw content".getBytes());

            setupAiMock("[\"Goal 1\", \"Goal 2\", \"Goal 3\"]");

            List<String> goals = service.extractGoals(txtFile);

            assertThat(goals).containsExactly("Goal 1", "Goal 2", "Goal 3");
        }

        @Test
        @DisplayName("Should handle AI response with markdown code block")
        void handlesMarkdownCodeBlock() throws IOException {
            PlanFile txtFile = new PlanFile(
                    "test.txt", "text/plain", "raw content".getBytes());

            // AI sometimes wraps in markdown
            setupAiMock("```json\n[\"Goal A\", \"Goal B\"]\n```");

            List<String> goals = service.extractGoals(txtFile);

            assertThat(goals).containsExactly("Goal A", "Goal B");
        }

        @Test
        @DisplayName("Should fallback to raw lines on AI failure")
        void fallbacksOnAiFailure() throws IOException {
            String content = "Line 1\nLine 2\nLine 3";
            PlanFile txtFile = new PlanFile(
                    "test.txt", "text/plain", content.getBytes());

            // Setup AI to throw exception
            when(chatClient.call(any(String.class), any(String.class)))
                    .thenThrow(new RuntimeException("AI unavailable"));

            List<String> goals = service.extractGoals(txtFile);

            // Should fallback to raw lines
            assertThat(goals).containsExactly("Line 1", "Line 2", "Line 3");
        }

        @Test
        @DisplayName("Should handle empty JSON array from AI")
        void handlesEmptyJsonArray() throws IOException {
            PlanFile txtFile = new PlanFile(
                    "test.txt", "text/plain", "content".getBytes());

            setupAiMock("[]");

            List<String> goals = service.extractGoals(txtFile);

            assertThat(goals).isEmpty();
        }
    }

    @Nested
    @DisplayName("File Type Detection Tests")
    class FileTypeDetectionTests {

        @Test
        @DisplayName("Should detect PDF by extension")
        void detectsPdfByExtension() throws IOException {
            // Note: Cannot test actual PDF parsing without valid PDF bytes
            // This test verifies the extension is recognized
            PlanFile pdfFile = new PlanFile(
                    "test.pdf", "application/pdf", "not-a-real-pdf".getBytes());

            // PDF parsing will fail but file type should be accepted
            assertThatThrownBy(() -> service.extractGoals(pdfFile))
                    .isInstanceOf(IOException.class); // PDFBox fails on invalid content
        }

        @Test
        @DisplayName("Should detect Excel by extension")
        void detectsExcelByExtension() throws IOException {
            PlanFile xlsxFile = new PlanFile(
                    "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "not-a-real-excel".getBytes());

            // Excel parsing will fail but file type should be accepted
            assertThatThrownBy(() -> service.extractGoals(xlsxFile))
                    .isInstanceOf(Exception.class); // POI fails on invalid content
        }

        @Test
        @DisplayName("Should accept file by extension even with wrong content-type")
        void acceptsByExtension() throws IOException {
            PlanFile csvFile = new PlanFile(
                    "test.csv", "application/octet-stream",
                    "col1,col2\nval1,val2".getBytes());

            setupAiMock("[\"val1 val2\"]");

            List<String> goals = service.extractGoals(csvFile);

            assertThat(goals).hasSize(1);
        }
    }

    private void setupAiMock(String aiResponse) {
        when(chatClient.call(any(String.class), any(String.class))).thenReturn(aiResponse);
    }
}
