package com.ai2qa.application.cache;

import com.ai2qa.application.planner.StepPlanner;
import com.ai2qa.domain.model.DomSnapshot;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter connecting GeminiClient to SmartDriver.
 *
 * <p>This bridges the existing AI infrastructure with the caching layer.
 */
@Component
public class GeminiSelectorFinder implements SmartDriver.SelectorFinder {

    private final StepPlanner.GeminiPlannerClient geminiClient;

    public GeminiSelectorFinder(StepPlanner.GeminiPlannerClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    @Override
    public Optional<String> findSelector(String elementDescription, DomSnapshot snapshot) {
        return geminiClient.findSelector(elementDescription, snapshot);
    }
}
