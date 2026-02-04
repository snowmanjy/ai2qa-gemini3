# GCP Deployment Plan - AI2QA (Hackathon Edition)

## Executive Summary

Deploy AI2QA to GCP using a **serverless-first architecture** optimized for:
- **Operational simplicity** (no VM management, no external storage setup)
- **Fast iteration** (instant deployments)
- **Pay-per-use** (scales to zero when idle)

**Architecture**: Cloud Run (API) + Cloud Run Jobs (Workers)

**Estimated Monthly Cost**: ~$20-50 (hackathon/low traffic)

---

## Architecture Overview

```
                    ┌─────────────────────────────────────────┐
                    │         Frontend (Cloud Run)            │
                    │         - Next.js Static Export         │
                    │         - Rewrites /api/* to API        │
                    └───────────────────┬─────────────────────┘
                                        │
                                        ▼
                    ┌─────────────────────────────────────────┐
                    │      Cloud Run (API Service)            │
                    │      - REST API endpoints               │
                    │      - Gemini AI integration            │
                    │      - Local ephemeral storage          │
                    │      - Scales 0-10 instances            │
                    └───────────────────┬─────────────────────┘
                                        │
                         ┌──────────────┼──────────────┐
                         │              │              │
                         ▼              ▼              ▼
                 ┌───────────┐  ┌───────────┐  ┌───────────┐
                 │ H2 Memory │  │ Ephemeral │  │ Cloud Run │
                 │ Database  │  │   Disk    │  │   Jobs    │
                 │           │  │ Artifacts │  │  Workers  │
                 └───────────┘  └───────────┘  └───────────┘
```

### Why This Architecture?

| Aspect | Benefit |
|--------|---------|
| **Gemini-only** | Single AI provider, simpler config |
| **No auth complexity** | Public demo mode for hackathon |
| **Scales to zero** | No cost when idle |
| **No VM management** | Google handles everything |
| **H2 database** | Zero database setup |
| **Ephemeral artifacts** | No Cloud Storage bucket needed |

### Artifact Storage Strategy

For hackathon demos, artifacts (screenshots, reports) use **local ephemeral disk**:
- Cloud Run provides ephemeral disk that persists while the container is warm
- Judge's experience is one session: URL → scan → view results → done
- Artifacts survive the entire demo session
- No GCS bucket, service account, or IAM setup required
- If container recycles after judge leaves, artifacts are cleaned up automatically

---

## Local Development (Recommended for Judges)

For hackathon evaluation, run locally without GCP:

```bash
# Prerequisites: Java 21, Node.js 20+, Chrome
./scripts/run-local.sh
```

This uses:
- **H2 in-memory database** (no PostgreSQL needed)
- **In-memory queues** (no Redis needed)
- **Local file storage** for screenshots/reports
- **Local Chrome automation**

---

## GCP Deployment (Optional)

### Phase 1: Project Setup

```bash
export PROJECT_ID="ai2qa-hackathon"
export REGION="us-central1"

gcloud projects create $PROJECT_ID --name="AI2QA Hackathon"
gcloud config set project $PROJECT_ID
```

### Phase 2: Enable APIs

```bash
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  aiplatform.googleapis.com
```

### Phase 3: Create Infrastructure

```bash
# Create Artifact Registry (for Docker images only)
gcloud artifacts repositories create ai2qa \
  --repository-format=docker \
  --location=$REGION
```

**Note:** No Cloud Storage bucket needed. Artifacts use Cloud Run's ephemeral disk.

### Phase 4: Deploy

```bash
# Build and push image
gcloud builds submit --tag ${REGION}-docker.pkg.dev/${PROJECT_ID}/ai2qa/backend:latest

# Deploy API
gcloud run deploy ai2qa-api \
  --image=${REGION}-docker.pkg.dev/${PROJECT_ID}/ai2qa/backend:latest \
  --region=$REGION \
  --memory=2Gi \
  --cpu=2 \
  --min-instances=0 \
  --max-instances=10 \
  --set-env-vars="GOOGLE_CLOUD_PROJECT=${PROJECT_ID},DAILY_CAP=50" \
  --allow-unauthenticated
```

---

## Secrets (Minimal for Hackathon)

| Secret | Required | Description |
|--------|----------|-------------|
| `cache-encryption-key` | Yes | Auto-generated |
| `recaptcha-secret-key` | Optional | reCAPTCHA v3 secret |

```bash
# Store secrets
openssl rand -base64 32 | gcloud secrets create cache-encryption-key --data-file=-
```

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GOOGLE_CLOUD_PROJECT` | GCP Project ID | Required |
| `AI_MODEL` | Gemini model | `gemini-2.5-flash` |
| `DAILY_CAP` | Max runs/day | `20` |
| `RECAPTCHA_ENABLED` | Enable abuse prevention | `false` |

---

## Cost Summary (Hackathon Edition)

| Service | Specification | Monthly Cost |
|---------|---------------|--------------|
| Cloud Run (API) | 2GB, scales 0-10 | ~$20-50 |
| Artifact Registry | 5GB (Docker images) | ~$1 |
| **Total** | | **~$20-55** |

*Note: No Cloud Storage or Cloud SQL costs. Uses H2 database and ephemeral disk.*

---

## Quick Reference Commands

```bash
# View API logs
gcloud run services logs read ai2qa-api --region=$REGION --limit=50

# Check service status
gcloud run services describe ai2qa-api --region=$REGION

# Redeploy after changes
gcloud builds submit --tag ${REGION}-docker.pkg.dev/${PROJECT_ID}/ai2qa/backend:latest
gcloud run deploy ai2qa-api --image=${REGION}-docker.pkg.dev/${PROJECT_ID}/ai2qa/backend:latest --region=$REGION
```

---

## Verification Checklist

### Local (Recommended)
- [ ] Java 21 installed
- [ ] Node.js 20+ installed
- [ ] `./scripts/run-local.sh` starts successfully
- [ ] App accessible at http://localhost:8080
- [ ] Test run creates and executes
- [ ] Screenshots visible in results

### Cloud (Optional)
- [ ] GCP project with billing enabled
- [ ] APIs enabled
- [ ] Image builds successfully
- [ ] Cloud Run service deployed
- [ ] Health check passes: `curl $API_URL/actuator/health`

---

**Built for the Google DeepMind Gemini 3 Hackathon (Feb 2026)**
