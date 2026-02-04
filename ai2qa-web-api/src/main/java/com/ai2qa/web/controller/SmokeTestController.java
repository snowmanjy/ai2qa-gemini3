package com.ai2qa.web.controller;

import com.ai2qa.application.run.TestRunService;
import com.ai2qa.application.run.view.TestRunView;
import com.ai2qa.web.dto.CreateTestRunRequest;
import com.ai2qa.web.dto.TestRunResponse;
import com.ai2qa.domain.context.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/smoke-test")
public class SmokeTestController {

    private final TestRunService testRunService;

    public SmokeTestController(
            TestRunService testRunService) {
        this.testRunService = testRunService;
    }

    @PostMapping
    public ResponseEntity<TestRunResponse> startSmokeTest(@Valid @RequestBody CreateTestRunRequest request) {
        String tenantId = TenantContext.getTenantId();
        TestRunView testRun = testRunService.createSmokeTest(
                tenantId,
                request.targetUrl(),
                request.goals(),
                request.persona(),
                request.cookiesJson());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(TestRunResponse.from(testRun));
    }
}
