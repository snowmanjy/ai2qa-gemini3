# Ai2QA - Product Requirements Document

**Google DeepMind Gemini 3 Hackathon Edition**

## Vision

Ai2QA is an autonomous AI testing platform that eliminates the need for manual test script maintenance. Using Google Gemini 3, it intelligently explores websites, discovers bugs, and heals broken tests automatically.

**Core Philosophy:** "Trust, but Verify" - AI drives, but logic verifies.

---

## Technical Architecture

### Backend Stack

- **Language:** Java 21 (LTS)
- **Framework:** Spring Boot 3.4
- **AI Provider:** Google Gemini 3 via Vertex AI (Spring AI)
- **Database:** H2 In-Memory (zero setup for hackathon)
- **Browser Driver:** Chrome DevTools MCP (Node.js subprocess via Playwright)

### Frontend Stack

- **Framework:** Next.js 16 (App Router)
- **UI:** React 19, Tailwind CSS, Shadcn UI

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        REST API                              │
│                    (ai2qa-web-api)                          │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                    Application Layer                         │
│                   (ai2qa-application)                       │
│    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│    │ Orchestrator │  │  Reflector   │  │ StepPlanner  │     │
│    └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      Domain Core                             │
│                  (ai2qa-domain-core)                        │
│         TestRun, ActionStep, DomSnapshot, Events             │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                    Infrastructure                            │
│  ┌──────────────┐  ┌──────────────┐                         │
│  │  JPA (H2)    │  │  MCP Bridge  │                         │
│  │  (infra-jpa) │  │ (mcp-bridge) │                         │
│  └──────────────┘  └──────────────┘                         │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Features

### 1. Self-Healing Tests

When a selector fails, Ai2QA:
1. Takes a DOM/accessibility snapshot
2. Asks Gemini AI to find the new selector
3. Continues execution without manual intervention

### 2. AI Persona System

Four specialized testing personas with unique behaviors:

| Persona | Behavior | Use Case |
|---------|----------|----------|
| **The Auditor** | Methodical, spec-focused | Regression testing |
| **The Gremlin** | Chaotic, edge-case finder | Stress testing |
| **The White Hat** | Security-focused probing | Vulnerability scanning |
| **Performance Hawk** | Core Web Vitals analysis | Performance testing |

### 3. Playwright Export (Zero Lock-in)

Export successful test runs as standard Playwright code:
- **Java:** JUnit 5 with Playwright Java bindings
- **TypeScript:** Playwright Test format

### 4. Aria Snapshot Mode

Uses accessibility tree snapshots for ~95% token savings:
- Elements identified by refs (`@e1`, `@e2`) instead of CSS selectors
- Dramatically reduces AI API costs
- More reliable element identification

---

## Security

### Prompt Injection Defense

- **Two-Stage Agent System:** Reader (no tools) extracts data; Executor (has tools) runs sanitized plans
- **DOM Sanitization:** HTML sanitization via Jsoup, removes scripts/iframes
- **Sandwich Defense:** Untrusted content wrapped in delimiters
- **Pattern Detection:** 17+ injection patterns blocked

### Target Domain Safety

- Self-protection: Blocks testing `ai2qa.com` (prevents infinite loops)
- Configurable blacklist via environment variables
- Rate limiting: Configurable daily cap

---

## API Overview

### Create Test Run

```http
POST /api/v1/test-runs
{
  "targetUrl": "https://example.com",
  "goals": ["Navigate to login", "Verify form validation"],
  "persona": "STANDARD"
}
```

### Get Test Run Status

```http
GET /api/v1/test-runs/{id}
```

### Export to Playwright

```http
GET /api/v1/test-runs/{id}/export/code?lang=JAVA
GET /api/v1/test-runs/{id}/export/code?lang=TYPESCRIPT
```

### Export Reports

```http
GET /api/v1/test-runs/{id}/export/pdf
GET /api/v1/test-runs/{id}/export/excel
```

---

## What Makes This Special

1. **No Test Scripts Required:** Just describe what you want to test in natural language
2. **Self-Healing:** Tests adapt to UI changes automatically
3. **Multi-Persona Testing:** Different AI personalities find different types of bugs
4. **Zero Lock-in:** Export to standard Playwright at any time
5. **Gemini 3 Powered:** Leverages Google's latest AI for intelligent test planning

---

## Future Vision

### Planned Personas

- **A11y Auditor:** WCAG compliance, screen reader testing
- **The Polyglot:** Multi-language/i18n validation
- **Compliance Officer:** Legal text, GDPR, cookie consent

### Platform Expansion

- **Ai2API:** API contract testing, SLA monitoring
- **Ai2DevOps:** Infrastructure validation, cost optimization

---

**Built for the Google DeepMind Gemini 3 Hackathon (Feb 2026)**
