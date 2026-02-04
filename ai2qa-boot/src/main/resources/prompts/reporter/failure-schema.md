{
  "status": "FAILURE",
  "goalOverview": "string - What did the user want to test? (1-2 sentences)",
  "outcomeShort": "string - One-line failure summary (max 100 chars)",
  "failureAnalysis": "string - Technical explanation of why it failed",
  "actionableFix": "string - Specific code change or selector fix needed",
  "keyAchievements": ["string - What worked before failure", "..."],
  "healthCheck": {
    "networkIssues": { "count": 0, "summary": "string - e.g. 'API 500 Error'" },
    "consoleIssues": { "count": 0, "summary": "string - e.g. 'Crash in <Navbar>'" },
    "accessibilityScore": "B",
    "accessibilitySummary": "string - e.g. '3 buttons missing labels'"
  }
}
