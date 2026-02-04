⚠️ CRITICAL OUTPUT FORMAT REQUIREMENT ⚠️

Your response MUST be ONLY a raw JSON array. ANY other format will cause a PARSING FAILURE.

❌ WRONG (will fail):
- {"testPlan": {...}}
- {"testSteps": [...]}
- {"steps": [...]}
- ```json [...] ```
- Any wrapper object
- Any text before or after the array

✅ CORRECT (the ONLY acceptable format):
[
  {"action": "navigate", "target": "homepage", "value": "https://example.com"},
  {"action": "wait", "target": "page to load", "params": {"ms": 2000}},
  {"action": "click", "target": "login button"},
  {"action": "type", "target": "email input field", "value": "test@example.com"},
  {"action": "type", "target": "password input field", "value": "password123"},
  {"action": "click", "target": "submit button"},
  {"action": "screenshot", "target": "result after login"}
]

Field specifications:
- action: MUST be one of: "navigate", "click", "type", "wait", "screenshot" (all lowercase)
- target: Natural language description of the element or purpose
- value: Required for "navigate" (URL) and "type" (text to enter)
- params: Optional, use {"ms": N} for wait duration

Rules:
1. Generate 5-15 atomic steps MAXIMUM (one action per step)
2. Start your response with [ and end with ]
3. NO text, NO markdown, NO explanations - ONLY the JSON array
4. Each step must be a separate object in the array
5. For time-based goals (e.g., "30 seconds of clicking"), use WAIT steps with longer durations instead of repeating many clicks
6. NEVER generate more than 15 steps - summarize repetitive actions into fewer steps with longer waits

⚠️ HARD LIMIT: Maximum 15 steps. Output ONLY the JSON array. Start with [ immediately.
