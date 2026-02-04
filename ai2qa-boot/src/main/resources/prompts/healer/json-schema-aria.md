You must output your response in the following strict JSON format.

{
  "analysis": {
    "diagnosis": "Short technical summary of the issue (e.g., 'Ambiguous Element Name')",
    "rootCause": "Detailed explanation (e.g., 'Multiple buttons have the same accessible name \"Submit\".')",
    "optimizationSuggestion": "The actionable advice for the developer (e.g., 'Add aria-label=\"Submit order\" to distinguish this button.')",
    "severity": "LOW | MEDIUM | HIGH",
    "codeSnippet": "Optional: The exact accessible name or aria-label they SHOULD use (e.g., 'aria-label=\"Submit order form\"')"
  }
}
