package com.ai2qa.application.report;

import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.model.TestRunStatus;
import com.ai2qa.domain.repository.TestRunRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Service for generating visual PDF reports with embedded screenshots.
 * Uses OpenPDF (iText fork) for PDF generation.
 */
@Service
public class PdfReportService {

    private static final Logger log = LoggerFactory.getLogger(PdfReportService.class);

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, Color.DARK_GRAY);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.DARK_GRAY);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.DARK_GRAY);
    private static final Font VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
    private static final Font SUCCESS_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(22, 163, 74));
    private static final Font FAILED_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(220, 38, 38));
    private static final Font CODE_FONT = FontFactory.getFont(FontFactory.COURIER, 9, Color.GRAY);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

    private static final float MAX_IMAGE_WIDTH = 720f;
    private static final float MAX_IMAGE_HEIGHT = 400f;
    private static final float PAGE_MARGIN = 36f;
    private static final float TALL_IMAGE_ASPECT_RATIO_THRESHOLD = 2.0f;

    private final TestRunRepository testRunRepository;
    private final ArtifactStorage artifactStorage;

    public PdfReportService(TestRunRepository testRunRepository, ArtifactStorage artifactStorage) {
        this.testRunRepository = testRunRepository;
        this.artifactStorage = artifactStorage;
    }

    /**
     * Generates a PDF report with screenshots for a test run.
     *
     * @param testRunId The test run ID
     * @return PDF file as byte array
     * @throws IOException if PDF generation fails
     */
    public byte[] generatePdfReport(String testRunId) throws IOException {
        TestRun testRun = testRunRepository.findById(new TestRunId(UUID.fromString(testRunId)))
                .orElseThrow(() -> new IllegalArgumentException("Test run not found: " + testRunId));

        log.info("Generating PDF report for test run: {}", testRunId);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.LETTER.rotate(), PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // Title
            Paragraph title = new Paragraph("Ai2QA Execution Report", TITLE_FONT);
            title.setSpacingAfter(20);
            document.add(title);

            // Summary section
            addSummarySection(document, testRun);

            // Test Results section
            addResultsSection(document, testRun);

            // Steps section
            addStepsSection(document, testRunId, testRun.getExecutedSteps());

            document.close();

            log.info("PDF report generated successfully: {} bytes", outputStream.size());
            return outputStream.toByteArray();

        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    private void addSummarySection(Document document, TestRun testRun) throws DocumentException {
        addLabelValue(document, "Run ID:", testRun.getId().value().toString().substring(0, 8));

        String url = testRun.getTargetUrl();
        if (url.length() > 60) {
            url = url.substring(0, 57) + "...";
        }
        addLabelValue(document, "Target URL:", url);

        boolean isPassed = testRun.getStatus() == TestRunStatus.COMPLETED;
        Paragraph statusPara = new Paragraph();
        statusPara.add(new Phrase("Status: ", LABEL_FONT));
        statusPara.add(new Phrase(testRun.getStatus().name(), isPassed ? SUCCESS_FONT : FAILED_FONT));
        statusPara.setSpacingAfter(5);
        document.add(statusPara);

        String persona = testRun.getPersona() != null ? testRun.getPersona().name() : TestPersona.DEFAULT_NAME;
        addLabelValue(document, "Persona:", persona);

        if (testRun.getFailureReason().isPresent()) {
            Paragraph failurePara = new Paragraph();
            failurePara.add(new Phrase("Failure Reason: ", FAILED_FONT));
            String reason = testRun.getFailureReason().get();
            if (reason.length() > 80) {
                reason = reason.substring(0, 77) + "...";
            }
            failurePara.add(new Phrase(reason, SMALL_FONT));
            failurePara.setSpacingAfter(10);
            document.add(failurePara);
        }
    }

    private void addResultsSection(Document document, TestRun testRun) throws DocumentException {
        Paragraph header = new Paragraph("Test Results", HEADER_FONT);
        header.setSpacingBefore(15);
        header.setSpacingAfter(10);
        document.add(header);

        List<ExecutedStep> steps = testRun.getExecutedSteps();
        long passedCount = steps.stream().filter(ExecutedStep::isSuccess).count();
        long failedCount = steps.stream().filter(ExecutedStep::isFailed).count();

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);

        addTableRow(table, "Passed:", String.valueOf(passedCount), SUCCESS_FONT);
        addTableRow(table, "Failed:", String.valueOf(failedCount), FAILED_FONT);
        addTableRow(table, "Total Steps:", String.valueOf(steps.size()), LABEL_FONT);

        document.add(table);
    }

    private void addStepsSection(Document document, String testRunId, List<ExecutedStep> steps)
            throws DocumentException, IOException {

        Paragraph header = new Paragraph("Execution Steps", HEADER_FONT);
        header.setSpacingBefore(20);
        header.setSpacingAfter(10);
        document.add(header);

        for (int i = 0; i < steps.size(); i++) {
            ExecutedStep step = steps.get(i);
            addStep(document, testRunId, step, i);
        }
    }

    private void addStep(Document document, String testRunId, ExecutedStep step, int index)
            throws DocumentException, IOException {

        boolean isFailed = step.isFailed();
        Font statusFont = isFailed ? FAILED_FONT : SUCCESS_FONT;

        String actionType = step.step() != null ? step.step().action() : "Action";
        String statusName = step.status() != null ? step.status().name() : "UNKNOWN";
        String stepText = String.format("Step #%d: %s - %s", index + 1, actionType, statusName);

        Paragraph stepHeader = new Paragraph(stepText, statusFont);
        stepHeader.setSpacingBefore(15);
        stepHeader.setSpacingAfter(5);
        document.add(stepHeader);

        if (step.selectorUsedOpt().isPresent()) {
            String selector = step.selectorUsed();
            if (selector.length() > 70) {
                selector = selector.substring(0, 67) + "...";
            }
            Paragraph selectorPara = new Paragraph("Selector: " + selector, CODE_FONT);
            selectorPara.setIndentationLeft(20);
            document.add(selectorPara);
        }

        if (isFailed && step.errorMessageOpt().isPresent()) {
            String error = step.errorMessage();
            if (error.length() > 70) {
                error = error.substring(0, 67) + "...";
            }
            Paragraph errorPara = new Paragraph("Error: " + error, FAILED_FONT);
            errorPara.setIndentationLeft(20);
            document.add(errorPara);
        }

        if (step.durationMs() > 0) {
            Paragraph durationPara = new Paragraph("Duration: " + step.durationMs() + "ms", SMALL_FONT);
            durationPara.setIndentationLeft(20);
            document.add(durationPara);
        }

        if (step.hasPerformanceMetrics()) {
            addPerformanceMetricsSection(document, step);
        }

        var screenshotBytes = artifactStorage.loadScreenshot(testRunId, index);
        if (screenshotBytes.isPresent()) {
            try {
                byte[] originalBytes = screenshotBytes.get();

                BufferedImage originalImg = ImageIO.read(new ByteArrayInputStream(originalBytes));
                if (originalImg == null) {
                    log.warn("Could not decode screenshot for step {}, skipping", index);
                    return;
                }

                int pixelWidth = originalImg.getWidth();
                int pixelHeight = originalImg.getHeight();
                float aspectRatio = (float) pixelHeight / pixelWidth;

                if (aspectRatio > TALL_IMAGE_ASPECT_RATIO_THRESHOLD) {
                    addChunkedTallImage(document, originalImg, index);
                } else {
                    byte[] compressedBytes = compressToJpeg(originalBytes, 0.85f);
                    Image image = Image.getInstance(compressedBytes);
                    image.scaleToFit(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
                    image.setSpacingBefore(10);
                    image.setSpacingAfter(10);
                    document.add(image);
                    log.debug("Embedded screenshot for step {} ({}x{}px scaled to fit {}x{}pt)",
                            index, pixelWidth, pixelHeight, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
                }
            } catch (Exception e) {
                log.warn("Failed to embed screenshot for step {}: {}", index, e.getMessage());
            }
        }
    }

    /**
     * Adds performance metrics section for measure_performance steps.
     */
    private void addPerformanceMetricsSection(Document document, ExecutedStep step) throws DocumentException {
        var metricsOpt = step.performanceMetricsOpt();
        if (metricsOpt.isEmpty()) {
            return;
        }

        var metrics = metricsOpt.get();

        Paragraph perfHeader = new Paragraph("Performance Metrics", HEADER_FONT);
        perfHeader.setSpacingBefore(10);
        perfHeader.setSpacingAfter(5);
        perfHeader.setIndentationLeft(20);
        document.add(perfHeader);

        if (!metrics.webVitals().isEmpty()) {
            PdfPTable vitalsTable = new PdfPTable(2);
            vitalsTable.setWidthPercentage(60);
            vitalsTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            vitalsTable.setSpacingBefore(5);

            metrics.getLcp().ifPresent(lcp -> {
                String lcpText = formatDuration(lcp);
                Font lcpFont = getMetricFont(lcp, 2500, 4000);
                addMetricRow(vitalsTable, "LCP (Largest Contentful Paint):", lcpText, lcpFont);
            });

            metrics.getCls().ifPresent(cls -> {
                String clsText = String.format("%.3f", cls);
                Font clsFont = getMetricFont(cls, 0.1, 0.25);
                addMetricRow(vitalsTable, "CLS (Cumulative Layout Shift):", clsText, clsFont);
            });

            metrics.getFcp().ifPresent(fcp -> {
                String fcpText = formatDuration(fcp);
                addMetricRow(vitalsTable, "FCP (First Contentful Paint):", fcpText, LABEL_FONT);
            });

            metrics.getTtfb().ifPresent(ttfb -> {
                String ttfbText = formatDuration(ttfb);
                Font ttfbFont = getMetricFont(ttfb, 800, 1800);
                addMetricRow(vitalsTable, "TTFB (Time to First Byte):", ttfbText, ttfbFont);
            });

            metrics.getPageLoad().ifPresent(load -> {
                String loadText = formatDuration(load);
                Font loadFont = getMetricFont(load, 3000, 5000);
                addMetricRow(vitalsTable, "Page Load Time:", loadText, loadFont);
            });

            vitalsTable.setSpacingAfter(10);
            document.add(vitalsTable);
        }

        if (!metrics.issues().isEmpty() && metrics.hasCriticalIssues()) {
            Paragraph issuesHeader = new Paragraph("Performance Issues:", LABEL_FONT);
            issuesHeader.setIndentationLeft(20);
            issuesHeader.setSpacingBefore(5);
            document.add(issuesHeader);

            for (var issue : metrics.issues()) {
                Font issueFont = "CRITICAL".equals(issue.severity()) || "HIGH".equals(issue.severity())
                        ? FAILED_FONT : SMALL_FONT;
                String prefix = "CRITICAL".equals(issue.severity()) ? "! " : "- ";
                Paragraph issuePara = new Paragraph(prefix + issue.message(), issueFont);
                issuePara.setIndentationLeft(30);
                document.add(issuePara);
            }
        }

        if (metrics.totalResources() != null && metrics.totalResources() > 0) {
            String resourceText = String.format("Resources: %d total", metrics.totalResources());
            if (metrics.totalTransferSizeKb() != null) {
                resourceText += String.format(" (%.1f MB transferred)",
                        metrics.totalTransferSizeKb() / 1024.0);
            }
            Paragraph resourcePara = new Paragraph(resourceText, SMALL_FONT);
            resourcePara.setIndentationLeft(20);
            resourcePara.setSpacingBefore(5);
            document.add(resourcePara);
        }
    }

    private String formatDuration(double ms) {
        if (ms >= 1000) {
            return String.format("%.2fs", ms / 1000);
        }
        return String.format("%.0fms", ms);
    }

    private Font getMetricFont(double value, double goodThreshold, double poorThreshold) {
        if (value < goodThreshold) {
            return SUCCESS_FONT;
        } else if (value >= poorThreshold) {
            return FAILED_FONT;
        }
        return FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(234, 179, 8));
    }

    private void addMetricRow(PdfPTable table, String label, String value, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, SMALL_FONT));
        labelCell.setBorder(0);
        labelCell.setPaddingBottom(3);
        labelCell.setPaddingLeft(20);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(0);
        valueCell.setPaddingBottom(3);
        table.addCell(valueCell);
    }

    private void addChunkedTallImage(Document document, BufferedImage originalImg, int stepIndex)
            throws DocumentException, IOException {

        int pixelWidth = originalImg.getWidth();
        int pixelHeight = originalImg.getHeight();

        float scale = MAX_IMAGE_WIDTH / pixelWidth;
        int chunkHeightPixels = (int) (MAX_IMAGE_HEIGHT / scale);

        int numChunks = (int) Math.ceil((double) pixelHeight / chunkHeightPixels);

        log.info("Chunking tall screenshot for step {} into {} pieces ({}x{}px, chunkPx={})",
                stepIndex, numChunks, pixelWidth, pixelHeight, chunkHeightPixels);

        for (int chunk = 0; chunk < numChunks; chunk++) {
            int yStart = chunk * chunkHeightPixels;
            int chunkHeight = Math.min(chunkHeightPixels, pixelHeight - yStart);

            BufferedImage subImg = originalImg.getSubimage(0, yStart, pixelWidth, chunkHeight);

            BufferedImage chunkImg = new BufferedImage(subImg.getWidth(), subImg.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = chunkImg.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, chunkImg.getWidth(), chunkImg.getHeight());
            g.drawImage(subImg, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
            ImageIO.write(chunkImg, "JPEG", jpegOut);
            byte[] jpegBytes = jpegOut.toByteArray();

            Image image = Image.getInstance(jpegBytes);
            image.scaleToFit(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
            image.setSpacingBefore(5);
            image.setSpacingAfter(5);

            document.add(image);

            log.info("Added chunk {}/{} for step {} (y={}..{}, {}px, scaled={}x{}, {}KB)",
                    chunk + 1, numChunks, stepIndex, yStart, yStart + chunkHeight, chunkHeight,
                    String.format("%.1f", image.getScaledWidth()),
                    String.format("%.1f", image.getScaledHeight()),
                    jpegBytes.length / 1024);
        }
    }

    private void addLabelValue(Document document, String label, String value) throws DocumentException {
        Paragraph para = new Paragraph();
        para.add(new Phrase(label + " ", LABEL_FONT));
        para.add(new Phrase(value, VALUE_FONT));
        para.setSpacingAfter(5);
        document.add(para);
    }

    private void addTableRow(PdfPTable table, String label, String value, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(0);
        labelCell.setPaddingBottom(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(0);
        valueCell.setPaddingBottom(5);
        table.addCell(valueCell);
    }

    /**
     * Compresses a PNG image to JPEG format to reduce file size.
     */
    private byte[] compressToJpeg(byte[] pngBytes, float quality) {
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (originalImage == null) {
                log.warn("Could not decode image for compression, using original");
                return pngBytes;
            }

            BufferedImage rgbImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = rgbImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            g.drawImage(originalImage, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "jpg", outputStream);

            byte[] compressedBytes = outputStream.toByteArray();
            log.debug("Compressed image from {} bytes to {} bytes ({}% reduction)",
                    pngBytes.length, compressedBytes.length,
                    (100 - (compressedBytes.length * 100 / pngBytes.length)));

            return compressedBytes;
        } catch (IOException e) {
            log.warn("Failed to compress image, using original: {}", e.getMessage());
            return pngBytes;
        }
    }
}
