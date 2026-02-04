# GCP Infrastructure Setup Scripts

**Hackathon Edition** - Simplified for Google DeepMind Gemini 3 Hackathon

These scripts set up the GCP infrastructure for AI2QA using a serverless-first architecture.

## Architecture

- **Cloud Run (API)**: Lightweight service (1GB RAM) for HTTP requests
- **Cloud Run Jobs (Worker)**: Heavyweight service (4GB RAM) for browser automation
- **Cloud SQL**: PostgreSQL 15 database (db-f1-micro, ~$10/month)
- **Memorystore Redis**: Session/queue storage (1GB, ~$35/month) - Optional for hackathon
- **Cloud Storage**: Screenshot and report artifacts
- **Workload Identity Federation**: Keyless GitHub Actions deployment

## Prerequisites

1. **GCP Project**: Already created (`ai2qa-484417`)
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

# Phase 5: Deploy Frontend (optional - can use run-local.sh instead)
./07-deploy-frontend.sh
```

## GitHub Repository Secrets

After running `04-setup-workload-identity.sh`, add these secrets to your GitHub repository:

| Secret | Description |
|--------|-------------|
| `GCP_PROJECT_ID` | `ai2qa-484417` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Output from script |
| `GCP_SERVICE_ACCOUNT` | `github-actions@ai2qa-484417.iam.gserviceaccount.com` |
| `NEXT_PUBLIC_RECAPTCHA_SITE_KEY` | (Optional) reCAPTCHA v3 site key |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GOOGLE_CLOUD_PROJECT` | GCP Project ID | Required |
| `DAILY_CAP` | Max test runs per day | `50` |
| `RECAPTCHA_ENABLED` | Enable reCAPTCHA v3 | `false` |

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
| Cloud Run Jobs (Worker) | ~$50-150 |
| Cloud SQL (db-f1-micro) | ~$10 |
| Memorystore Redis (1GB) | ~$35 (optional) |
| Cloud Storage | ~$2 |
| **Total (Low Traffic)** | **~$85-150** |

## Local Development (No GCP Required)

For hackathon judges, run locally without GCP:

```bash
# From project root
./scripts/run-local.sh
```

This uses:
- H2 in-memory database (no PostgreSQL)
- In-memory queues (no Redis)
- Local Chrome automation

## Troubleshooting

### VPC Connector Issues
```bash
# Check connector status
gcloud compute networks vpc-access connectors describe ai2qa-connector --region=us-central1
```

### Cloud SQL Connection
```bash
# Test connection from Cloud Shell
gcloud sql connect ai2qa-db --user=ai2qa --database=ai2qa
```

### View Logs
```bash
# API service logs
gcloud run services logs read ai2qa-api --region=us-central1

# Worker job logs
gcloud run jobs executions list --job=ai2qa-worker --region=us-central1
```
