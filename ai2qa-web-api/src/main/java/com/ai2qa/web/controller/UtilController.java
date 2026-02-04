package com.ai2qa.web.controller;

import com.ai2qa.application.extraction.PlanExtractionService;
import com.ai2qa.application.extraction.PlanFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Utility controller for file processing operations.
 */
@RestController
@RequestMapping("/api/v1/utils")
public class UtilController {

    private final PlanExtractionService planExtractionService;

    public UtilController(PlanExtractionService planExtractionService) {
        this.planExtractionService = planExtractionService;
    }

    /**
     * Extracts test goals from an uploaded file (PDF, Excel, CSV, TXT).
     *
     * @param file The uploaded file
     * @return Map containing extracted goals
     */
    @PostMapping("/extract-plan")
    public ResponseEntity<Map<String, List<String>>> extractPlan(
            @RequestParam("file") MultipartFile file) throws IOException {
        PlanFile planFile = new PlanFile(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        List<String> goals = planExtractionService.extractGoals(planFile);
        return ResponseEntity.ok(Map.of("goals", goals));
    }
}
