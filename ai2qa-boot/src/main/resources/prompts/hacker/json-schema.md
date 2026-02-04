## JSON Schema for Security Test Plan

Return a JSON array of security test steps:

```json
{
  "type": "array",
  "items": {
    "type": "object",
    "required": ["action", "target"],
    "properties": {
      "action": {
        "type": "string",
        "enum": ["navigate", "click", "type", "wait", "screenshot", "verify"],
        "description": "The action to perform"
      },
      "target": {
        "type": "string",
        "description": "Human-readable description of the target element or condition"
      },
      "value": {
        "type": "string",
        "description": "Value for type actions or URL for navigate actions"
      },
      "expected": {
        "type": "string",
        "description": "Expected result for verify actions"
      },
      "securityContext": {
        "type": "string",
        "description": "Explains the security vulnerability being tested and expected secure behavior"
      },
      "attackVector": {
        "type": "string",
        "enum": [
          "SQL_INJECTION",
          "XSS",
          "CSRF",
          "SSRF",
          "AUTH_BYPASS",
          "PRIVILEGE_ESCALATION",
          "IDOR",
          "COMMAND_INJECTION",
          "XXE",
          "RACE_CONDITION",
          "BUSINESS_LOGIC",
          "RATE_LIMIT",
          "SENSITIVE_DATA_EXPOSURE",
          "SECURITY_MISCONFIGURATION"
        ],
        "description": "The OWASP category or attack type being tested"
      },
      "severity": {
        "type": "string",
        "enum": ["CRITICAL", "HIGH", "MEDIUM", "LOW"],
        "description": "Risk severity if this vulnerability exists"
      },
      "params": {
        "type": "object",
        "description": "Additional parameters (e.g., timeout for wait actions)"
      }
    }
  }
}
```

## Example Output

```json
[
  {
    "action": "navigate",
    "target": "login page",
    "value": "https://example.com/login",
    "securityContext": "Starting authentication security test",
    "attackVector": "AUTH_BYPASS",
    "severity": "CRITICAL"
  },
  {
    "action": "type",
    "target": "email field",
    "value": "admin' OR '1'='1'--",
    "securityContext": "SQL Injection payload to test input sanitization",
    "attackVector": "SQL_INJECTION",
    "severity": "CRITICAL"
  },
  {
    "action": "type",
    "target": "password field",
    "value": "anything"
  },
  {
    "action": "click",
    "target": "login button"
  },
  {
    "action": "verify",
    "target": "error message or URL",
    "expected": "Should show 'Invalid credentials' OR stay on login page (NOT redirect to dashboard)",
    "securityContext": "Application must reject SQL injection payload and NOT bypass authentication",
    "attackVector": "SQL_INJECTION",
    "severity": "CRITICAL"
  },
  {
    "action": "screenshot",
    "target": "authentication test result",
    "securityContext": "Capture evidence that SQL injection was properly blocked"
  }
]
```

## Important Notes

1. **securityContext** field is REQUIRED for all security-relevant steps
2. **attackVector** and **severity** help categorize findings in security reports
3. **verify** actions must clearly state SECURE expected behavior
4. Every attack payload should be followed by verification that it was blocked
5. Screenshots should capture both attack attempt AND security response
