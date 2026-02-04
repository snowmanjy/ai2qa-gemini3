package com.ai2qa.domain.model;

import java.util.Map;
import java.util.Optional;

/**
 * Represents a planned action step to be executed.
 *
 * <p>Pure data record - no validation logic.
 *
 * @param stepId     Unique identifier for this step
 * @param action     The action type (e.g., "navigate", "click", "type")
 * @param target     Target description (e.g., "login button", "email input")
 * @param selector   Optional CSS/XPath selector
 * @param value      Optional input value for type actions
 * @param params     Additional parameters (stringified)
 */
public record ActionStep(
        String stepId,
        String action,
        String target,
        Optional<String> selector,
        Optional<String> value,
        Map<String, String> params
) { }
