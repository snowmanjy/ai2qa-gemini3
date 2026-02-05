# GCP Infrastructure Setup Scripts

**Hackathon Edition** - Simplified for Google DeepMind Gemini 3 Hackathon

These scripts set up the GCP infrastructure for AI2QA using a serverless-first architecture.

## Live Demo URLs

| Service | URL |
|---------|-----|
| **Frontend** | https://ai2qa-frontend-594181886138.us-central1.run.app |
| **Backend API** | https://ai2qa-api-594181886138.us-central1.run.app |

## Architecture

- **Cloud Run (API)**: Lightweight service (2GB RAM) for HTTP requests
- **Cloud SQL**: PostgreSQL 15 database (db-f1-micro)
- **Cloud Storage**: Screenshot and report artifacts
- **Vertex AI**: Gemini 3 Flash (preview) for AI-powered testing
- **Workload Identity Federation**: Keyless GitHub Actions deployment

> **Note**: This hackathon edition uses in-memory queues instead of Redis for simplicity.

## Prerequisites

1. **GCP Project**: `ai2qa-hackathon-demo`
2. **gcloud CLI**: Installed and authenticated
3. **Billing**: Enabled on the GCP project

```bash
# Authenticate
gcloud auth login
gcloud auth application-default login
```

## Execution Order

Run scripts in order:

```bash
# Phase 1: Project Foundation
./01-setup-project.sh

# Phase 1.3: Service Account
./02-create-service-account.sh

# Phase 2: Core Infrastructure (takes 10-15 minutes)
./03-create-infrastructure.sh

# Phase 3: Workload Identity Federation for GitHub
./04-setup-workload-identity.sh

# Phase 6: Store Production Secrets
./05-store-secrets.sh

# Phase 4: Initial Deployment
./06-deploy.sh

# Phase 5: Deploy Frontend
./07-deploy-frontend.sh
```

## GitHub Actions Auto-Deployment

After running the scripts, add these secrets to your GitHub repository (`snowmanjy/ai2qa-gemini3`):

| Secret | Value |
|--------|-------|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/594181886138/locations/global/workloadIdentityPools/github-pool/providers/github-provider` |
| `GCP_SERVICE_ACCOUNT` | `github-actions@ai2qa-hackathon-demo.iam.gserviceaccount.com` |

The workflows in `.github/workflows/` will auto-deploy on push to `main`:
- `deploy-backend.yml` - Deploys when `ai2qa-*/**` files change
- `deploy-frontend.yml` - Deploys when `frontend/**` files change

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GOOGLE_CLOUD_PROJECT` | GCP Project ID | `ai2qa-hackathon-demo` |
| `DAILY_CAP` | Max test runs per day | `50` |
| `RECAPTCHA_ENABLED` | Enable reCAPTCHA v3 | `true` |
| `VERTEX_AI_MODEL` | Gemini model | `gemini-3-flash-preview` |

## Updating Production Secrets

```bash
# Example: Update reCAPTCHA secret key
echo -n '6Le...' | gcloud secrets versions add recaptcha-secret-key --data-file=-

# Verify secret
gcloud secrets versions access latest --secret=recaptcha-secret-key
```

## Estimated Monthly Cost

| Service | Cost |
|---------|------|
| Cloud Run (API) | ~$20-50 |
| Cloud SQL (db-f1-micro) | ~$10 |
| Cloud Storage | ~$2 |
| Vertex AI (Gemini 3) | ~$10-50 (usage-based) |
| **Total (Low Traffic)** | **~$40-110** |

## Local Development (No GCP Required)

For hackathon judges, run locally without GCP:

```bash
# From project root
./scripts/run-local.sh
```

This uses:
- H2 in-memory database (no PostgreSQL)
- In-memory queues
- Local Chrome automation
- Gemini 3 Flash (requires GOOGLE_CLOUD_PROJECT env var)

## Troubleshooting

### VPC Connector Issues
```bash
# Check connector status
gcloud compute networks vpc-access connectors describe ai2qa-demo-connector --region=us-central1 --project=ai2qa-hackathon-demo
```

### Cloud SQL Connection
```bash
# Test connection from Cloud Shell
gcloud sql connect ai2qa-demo-db --user=ai2qa --database=ai2qa --project=ai2qa-hackathon-demo
```

### View Logs
```bash
# API service logs
gcloud run services logs read ai2qa-api --region=us-central1 --project=ai2qa-hackathon-demo

# Check API health
curl https://ai2qa-api-594181886138.us-central1.run.app/actuator/health
```
