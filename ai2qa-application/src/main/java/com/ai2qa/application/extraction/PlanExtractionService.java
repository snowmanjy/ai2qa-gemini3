package com.ai2qa.application.extraction;

import com.ai2qa.application.port.ChatClientPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting test goals from uploaded files (PDF, Excel, CSV, TXT).
 * Uses AI to refine and structure the extracted text into actionable test
 * goals.
 */
@Service
public class PlanExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PlanExtractionService.class);

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int MAX_TEXT_LENGTH = 15000; // Token budget ~4k tokens
    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "text/plain");

    private static final String SYSTEM_PROMPT = """
            You are a QA Lead. I will provide raw text extracted from a file (PDF or Spreadsheet).
            It may contain messy formatting, headers, or column pipes.

            Task:
            - Identify the actual test steps/goals (the actionable items to test).
            - SKIP the first row if it contains column headers like 'Test ID', 'Test Name', 'Steps', 'Expected Result', 'Priority', etc.
            - Ignore any row that looks like a table header (contains words like ID, Name, Description, Result, Status).
            - Ignore legal disclaimers, document titles, or fluff.
            - Combine related columns into a single goal (e.g., "Test Name: do X" + "Expected: Y happens" = "Do X and verify Y happens").

            Output valid JSON array of strings: ["Goal 1", "Goal 2", ...]
            Maximum 10 goals. Only output the JSON array, nothing else.
            """;

    private final ChatClientPort chatClient;
    private final ObjectMapper objectMapper;

    public PlanExtractionService(
            @Qualifier("plannerChatPort") ChatClientPort chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts test goals from an uploaded file.
     *
     * @param file The uploaded file (PDF, Excel, CSV, or TXT)
     * @return List of extracted and refined test goals
     * @throws IllegalArgumentException if file type or size is invalid
     * @throws IOException              if file parsing fails
     */
    public List<String> extractGoals(PlanFile file) throws IOException {
        // Validate file
        validateFile(file);

        // Extract raw text based on file type
        String rawText = extractText(file);
        log.debug("Extracted {} chars from file: {}", rawText.length(), file.filename());

        // Truncate to token budget
        if (rawText.length() > MAX_TEXT_LENGTH) {
            rawText = rawText.substring(0, MAX_TEXT_LENGTH) + "\n[Content truncated for processing]";
        }

        // Use AI to refine into goals
        return refineWithAI(rawText);
    }

    private void validateFile(PlanFile file) {
        if (file == null || file.size() == 0) {
            throw new IllegalArgumentException("File is required");
        }

        if (file.size() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 2MB limit");
        }

        String contentType = file.contentType();
        String filename = file.filename();

        // Check by content type or extension
        boolean validType = contentType != null && ALLOWED_TYPES.contains(contentType);
        boolean validExtension = filename != null && (filename.endsWith(".pdf") ||
                filename.endsWith(".xlsx") ||
                filename.endsWith(".xls") ||
                filename.endsWith(".csv") ||
                filename.endsWith(".txt"));

        if (!validType && !validExtension) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: PDF, Excel, CSV, TXT");
        }
    }

    private String extractText(PlanFile file) throws IOException {
        String filename = file.filename().toLowerCase();

        if (filename.endsWith(".pdf")) {
            return extractFromPdf(file);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return extractFromExcel(file);
        } else if (filename.endsWith(".csv")) {
            return extractFromCsv(file);
        } else {
            // Fallback: treat as plain text
            return extractFromText(file);
        }
    }

    private String extractFromPdf(PlanFile file) throws IOException {
        PdfReader reader = new PdfReader(file.contentBytes());
        try {
            StringBuilder text = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                text.append(extractor.getTextFromPage(i));
                text.append("\n");
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    private String extractFromExcel(PlanFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.contentBytes()))) {
            Sheet sheet = workbook.getSheetAt(0); // First sheet only

            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    String value = getCellValueAsString(cell);
                    if (!value.isBlank()) {
                        cells.add(value.trim());
                    }
                }
                if (!cells.isEmpty()) {
                    sb.append(String.join(" | ", cells)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private String extractFromCsv(PlanFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(file.contentBytes()), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {

            for (CSVRecord record : parser) {
                List<String> values = new ArrayList<>();
                for (String value : record) {
                    if (!value.isBlank()) {
                        values.add(value.trim());
                    }
                }
                if (!values.isEmpty()) {
                    sb.append(String.join(" ", values)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String extractFromText(PlanFile file) {
        return new String(file.contentBytes(), StandardCharsets.UTF_8);
    }

    private List<String> refineWithAI(String rawText) {
        try {
            if (rawText == null || rawText.isBlank()) {
                return List.of();
            }
            String response = chatClient.call(SYSTEM_PROMPT, rawText);

            // Parse JSON response
            String jsonContent = extractJsonArray(response);
            return objectMapper.readValue(jsonContent, new TypeReference<List<String>>() {
            });

        } catch (Exception e) {
            log.error("AI refinement failed, returning raw lines", e);
            // Fallback: split by lines and return non-empty ones
            return rawText.lines()
                    .filter(line -> !line.isBlank())
                    .limit(10)
                    .toList();
        }
    }

    private String extractJsonArray(String response) {
        // Find JSON array in response (handle markdown code blocks)
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "[]";
    }
}
