Respond with a JSON object containing extracted insights and structured patterns from the test run.

```json
{
  "insights": [
    {
      "contextTag": "framework:react",
      "insightText": "React hydration errors can be avoided by waiting 500ms after initial page load"
    },
    {
      "contextTag": "selector:data-testid",
      "insightText": "This site uses data-testid attributes consistently - prefer these over CSS classes"
    }
  ],
  "sitePatterns": [
    {
      "patternType": "SELECTOR",
      "key": "login_button",
      "selector": "[data-testid='login-btn']",
      "selectorType": "CSS",
      "alternatives": [
        { "selector": "button[type='submit']", "selectorType": "CSS" }
      ]
    },
    {
      "patternType": "TIMING",
      "key": "hydration_wait",
      "value": "500",
      "description": "Wait for React hydration"
    },
    {
      "patternType": "QUIRK",
      "key": "modal_close_escape",
      "value": "Press Escape twice to close modals",
      "description": "Single Escape press is intercepted by nested modal"
    }
  ],
  "frameworkDetected": "react",
  "learningConfidence": "high"
}
```

**Schema:**

## Main Object
- `insights` (array, required): List of text-based insights (backward compatible)
  - `contextTag` (string): Category:subcategory format (e.g., "framework:react")
  - `insightText` (string): Concise, actionable insight (max 200 chars)
- `sitePatterns` (array, optional): Structured patterns for the knowledge base
  - See "Site Patterns" section below
- `frameworkDetected` (string, optional): Detected frontend framework if identifiable
- `learningConfidence` (string): "high", "medium", or "low"

## Site Patterns (NEW)
Each pattern in `sitePatterns` has:
- `patternType` (string, required): One of:
  - `SELECTOR` - CSS/XPath/ARIA selector for finding elements
  - `TIMING` - Wait time or timing configuration
  - `AUTH` - Authentication-related pattern
  - `QUIRK` - Site-specific workaround or quirk
- `key` (string, required): Identifier for this pattern (e.g., "login_button", "submit_form")
- `selector` (string): The selector value (for SELECTOR type)
- `selectorType` (string): One of: CSS, XPATH, TEXT, ARIA, DATA_TESTID
- `value` (string): The configuration value (for TIMING, AUTH, QUIRK types)
- `description` (string, optional): Human-readable description
- `alternatives` (array, optional): Alternative selectors to try
  - `selector` (string): Alternative selector value
  - `selectorType` (string): Type of alternative selector

**Rules:**
- Return empty `insights: []` if nothing valuable to learn
- Extract `sitePatterns` when you identify reliable selectors or patterns
- Each contextTag MUST follow the taxonomy format: `category:subcategory`
- Each insightText MUST be a single, actionable sentence
- Maximum 5 insights and 10 sitePatterns per test run
- Prefer `data-testid` and `aria-label` selectors over CSS classes
- For SELECTOR patterns, always try to provide at least one alternative
- Prefer quality over quantity
