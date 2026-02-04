package com.ai2qa.application.run.view;

import java.util.Map;
import java.util.Optional;

public record ActionStepView(
        String stepId,
        String action,
        String target,
        Optional<String> selector,
        Optional<String> value,
        Map<String, String> params
) {
    public ActionStepView {
        Map<String, String> safeParams = params == null ? Map.of() : Map.copyOf(params);
        Optional<String> safeSelector = selector == null ? Optional.empty() : selector;
        Optional<String> safeValue = value == null ? Optional.empty() : value;
        params = safeParams;
        selector = safeSelector;
        value = safeValue;
    }
}
