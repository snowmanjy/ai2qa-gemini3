package com.ai2qa.application.report;

import com.ai2qa.application.event.TestRunCompletedSpringEvent;
import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.domain.model.RunSummary;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.repository.TestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listener that generates summaries and PDF reports asynchronously
 * when a test run completes.
 */
@Component
public class ReportGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationListener.class);
    private static final String REPORT_FILENAME = "report.pdf";

    private final ReportWriterService reportWriterService;
    private final PdfReportService pdfReportService;
    private final ArtifactStorage artifactStorage;
    private final TestRunRepository testRunRepository;

    public ReportGenerationListener(
            ReportWriterService reportWriterService,
            PdfReportService pdfReportService,
            ArtifactStorage artifactStorage,
            TestRunRepository testRunRepository) {
        this.reportWriterService = reportWriterService;
        this.pdfReportService = pdfReportService;
        this.artifactStorage = artifactStorage;
        this.testRunRepository = testRunRepository;
    }

    /**
     * Generates summary and PDF report when a test run completes.
     * Runs asynchronously to avoid blocking the main thread.
     *
     * <p>Updates summaryStatus to track progress:
     * <ul>
     *   <li>GENERATING - when AI summary generation starts</li>
     *   <li>COMPLETED - when summary is successfully generated (set by setSummary)</li>
     *   <li>FAILED - when an error occurs during generation</li>
     * </ul>
     */
    @Async
    @EventListener
    public void onTestRunCompleted(TestRunCompletedSpringEvent event) {
        TestRun testRun = event.getTestRun();
        String testRunId = testRun.getId().value().toString();

        log.info("Starting report generation for Run #{}", testRunId.substring(0, 8));

        try {
            // Step 0: Mark summary as generating so frontend shows loading state
            testRun.markSummaryGenerating();
            testRunRepository.save(testRun);

            // Step 1: Generate the summary using AI
            log.debug("Generating summary for Run #{}", testRunId.substring(0, 8));
            RunSummary summary = reportWriterService.generateSummary(testRun);
            testRun.setSummary(summary);  // This also sets summaryStatus to COMPLETED

            // Step 2: Persist the summary to the database
            testRunRepository.save(testRun);
            log.info("Summary generated and saved for Run #{}: {}",
                    testRunId.substring(0, 8), summary.status());

            // Step 3: Generate the PDF report (now includes summary data)
            byte[] pdfBytes = pdfReportService.generatePdfReport(testRunId);

            // Step 4: Save PDF to artifact storage
            artifactStorage.saveReport(testRunId, REPORT_FILENAME, pdfBytes);

            log.info("PDF Report saved for Run #{} ({} bytes)", testRunId.substring(0, 8), pdfBytes.length);

        } catch (Throwable e) {
            // Catch Throwable to handle ExceptionInInitializerError from PDFBox font scanning
            log.error("Failed to generate report for Run #{}: {}", testRunId.substring(0, 8), e.getMessage(), e);

            // Mark summary as failed so frontend can show appropriate message
            try {
                testRun.markSummaryFailed();
                testRunRepository.save(testRun);
            } catch (Exception saveError) {
                log.error("Failed to save summary failure status: {}", saveError.getMessage());
            }
        }
    }
}
