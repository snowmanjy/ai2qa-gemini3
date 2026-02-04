# Ai2QA - AI-Powered Website Testing with Gemini 3

**Google DeepMind Gemini 3 Hackathon Submission**

Ai2QA is an autonomous AI testing platform that uses Google Gemini to explore, test, and find bugs in websites - without writing a single line of test code.

## What Makes Ai2QA Unique

- **Self-Healing Tests**: When UI changes break selectors, AI finds new ones automatically
- **Four AI Personas**: Different testing strategies for different needs
- **Zero Setup**: Just enter a URL and let AI do the rest
- **No Vendor Lock-in**: Export tests to standard Playwright code

## The Four AI Personas

| Persona | Role | What It Does |
|---------|------|--------------|
| **The Auditor** | Standard QA | Methodical regression testing, validates business logic |
| **The Gremlin** | Chaos Engineer | Rage clicks, edge cases, state corruption testing |
| **The White Hat** | Security Auditor | XSS probing, SQL injection, auth bypass attempts |
| **Performance Hawk** | Performance Analyst | Core Web Vitals, load times, optimization suggestions |

## Quick Start

### Prerequisites

- **Java 21** (required)
- **Node.js 20+** (for MCP browser automation)
- **Google Cloud SDK** (for Gemini API access)

### 1. Authenticate with Google Cloud

```bash
gcloud auth application-default login
export GOOGLE_CLOUD_PROJECT=your-project-id
```

### 2. Run Locally (No Docker Required!)

```bash
./scripts/run-local.sh
```

Or manually:

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar ai2qa-boot/target/ai2qa-boot-*.jar
```

### 3. Open in Browser

- **App**: http://localhost:8080
- **H2 Console** (for debugging): http://localhost:8080/h2-console

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `GOOGLE_CLOUD_PROJECT` | GCP Project ID for Vertex AI | Required |
| `AI_MODEL` | Gemini model to use | `gemini-2.5-flash` |
| `DAILY_CAP` | Max test runs per day | `20` |
| `RECAPTCHA_ENABLED` | Enable reCAPTCHA v3 | `false` |

## Architecture

```
frontend/              # Next.js 16 (React 19) web UI
ai2qa-boot/            # Spring Boot 3.4 main application
ai2qa-application/     # Business logic & AI orchestration
ai2qa-domain-core/     # Pure Java domain models
ai2qa-infra-jpa/       # H2 database (in-memory)
ai2qa-mcp-bridge/      # Chrome DevTools MCP integration
ai2qa-web-api/         # REST API controllers
```

## How It Works

1. **User enters a URL** and selects a testing persona
2. **AI Planner** (Gemini) generates a test plan from natural language goals
3. **AI Executor** navigates the browser via Chrome DevTools Protocol
4. **Self-Healing**: If selectors fail, AI analyzes the DOM and finds new ones
5. **Reports**: Screenshots, execution logs, and exportable Playwright code

## Key Technologies

- **AI**: Google Gemini 3 (via Vertex AI + Spring AI)
- **Browser Automation**: Chrome DevTools MCP (Playwright engine)
- **Backend**: Spring Boot 3.4, Java 21
- **Frontend**: Next.js 16, React 19, Tailwind CSS
- **Database**: H2 In-Memory (zero setup)

## API Endpoints

### Create a Test Run

```http
POST /api/v1/test-runs
Content-Type: application/json

{
  "targetUrl": "https://example.com",
  "goals": [
    "Click all navigation links",
    "Fill out the contact form",
    "Verify form submission works"
  ],
  "persona": "STANDARD"
}
```

### Export to Playwright

```http
GET /api/v1/test-runs/{id}/export/code?lang=JAVA
GET /api/v1/test-runs/{id}/export/code?lang=TYPESCRIPT
```

## Demo Limits

For the hackathon demo:
- **20 test runs per day** (configurable via `DAILY_CAP`)
- **No authentication required** (public demo mode)
- **reCAPTCHA optional** (disabled by default for local dev)

## Running with Docker (Optional)

```bash
docker-compose up
```

Or deploy to Cloud Run:

```bash
gcloud builds submit --tag gcr.io/${PROJECT_ID}/ai2qa-hackathon
gcloud run deploy ai2qa-hackathon \
  --image gcr.io/${PROJECT_ID}/ai2qa-hackathon \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 2Gi
```

## License

Proprietary - Hackathon Demo Only

---

**Built for the Google DeepMind Gemini 3 Hackathon (Feb 2026)**
