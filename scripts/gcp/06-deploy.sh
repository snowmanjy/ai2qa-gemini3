#!/bin/bash
# =============================================================================
# GCP Infrastructure Setup - Phase 4: Deploy Backend Services
# =============================================================================
# Hackathon Edition: Gemini-only, no Clerk/Stripe/PostHog
# =============================================================================

set -e

export PROJECT_ID="${PROJECT_ID:-ai2qa-484417}"
export REGION="${REGION:-us-central1}"

echo "=== Phase 4: Deploy Backend Services (Hackathon Edition) ==="

# Get infrastructure details
export SA_EMAIL="ai2qa-app@${PROJECT_ID}.iam.gserviceaccount.com"
export CLOUD_SQL_INSTANCE=$(gcloud sql instances describe ai2qa-db --format="value(connectionName)")
export REDIS_HOST=$(gcloud redis instances describe ai2qa-redis --region=$REGION --format="value(host)" 2>/dev/null || echo "")
export DOCKER_REPO="${REGION}-docker.pkg.dev/${PROJECT_ID}/ai2qa"

echo ""
echo "Configuration:"
echo "  Service Account: $SA_EMAIL"
echo "  Cloud SQL: $CLOUD_SQL_INSTANCE"
echo "  Redis Host: ${REDIS_HOST:-NOT CONFIGURED (using in-memory)}"
echo "  Docker Repo: $DOCKER_REPO"

# -----------------------------------------------------------------------------
# Build and Push Docker Image
# -----------------------------------------------------------------------------
echo ""
echo ">>> Building Docker image..."
cd "$(dirname "$0")/../.."

# Configure Docker for Artifact Registry
gcloud auth configure-docker ${REGION}-docker.pkg.dev --quiet

# Build with Cloud Build (faster, no local Docker needed)
echo ">>> Submitting build to Cloud Build..."
gcloud builds submit \
  --tag ${DOCKER_REPO}/backend:latest \
  --timeout=1800s \
  --quiet

# -----------------------------------------------------------------------------
# Deploy API Service (Lightweight - no browser)
# -----------------------------------------------------------------------------
echo ""
echo ">>> Deploying API Service..."

# Build env vars string
ENV_VARS="SPRING_PROFILES_ACTIVE=api,prod"
ENV_VARS="${ENV_VARS}@CLOUD_SQL_INSTANCE=${CLOUD_SQL_INSTANCE}"
ENV_VARS="${ENV_VARS}@GOOGLE_CLOUD_PROJECT=${PROJECT_ID}"
ENV_VARS="${ENV_VARS}@AI2QA_CLOUD_URL=https://ai2qa-api-${PROJECT_ID}.a.run.app"
ENV_VARS="${ENV_VARS}@SAFE_BROWSING_ENABLED=${SAFE_BROWSING_ENABLED:-false}"
ENV_VARS="${ENV_VARS}@AI2QA_BROWSER_ENGINE=playwright"
ENV_VARS="${ENV_VARS}@AI2QA_BROWSER_SNAPSHOT_MODE=aria"
ENV_VARS="${ENV_VARS}@DAILY_CAP=${DAILY_CAP:-50}"
ENV_VARS="${ENV_VARS}@RECAPTCHA_ENABLED=${RECAPTCHA_ENABLED:-false}"

# Add Redis host if configured
if [ -n "$REDIS_HOST" ]; then
  ENV_VARS="${ENV_VARS}@SPRING_DATA_REDIS_HOST=${REDIS_HOST}"
fi

# Build secrets string (only required secrets)
SECRETS="CLOUD_SQL_PASSWORD=db-password:latest"
SECRETS="${SECRETS},CACHE_ENCRYPTION_KEY=cache-encryption-key:latest"

# Add optional secrets if they exist
if gcloud secrets describe gmail-app-password --quiet 2>/dev/null; then
  SECRETS="${SECRETS},GMAIL_APP_PASSWORD=gmail-app-password:latest"
fi
if gcloud secrets describe google-safe-browsing-api-key --quiet 2>/dev/null; then
  SECRETS="${SECRETS},GOOGLE_SAFE_BROWSING_API_KEY=google-safe-browsing-api-key:latest"
fi
if gcloud secrets describe recaptcha-secret-key --quiet 2>/dev/null; then
  SECRETS="${SECRETS},RECAPTCHA_SECRET_KEY=recaptcha-secret-key:latest"
fi

gcloud run deploy ai2qa-api \
  --image="${DOCKER_REPO}/backend:latest" \
  --region="$REGION" \
  --memory=1Gi \
  --cpu=1 \
  --min-instances=0 \
  --max-instances=10 \
  --concurrency=80 \
  --timeout=60s \
  --service-account="$SA_EMAIL" \
  --set-env-vars="^@^${ENV_VARS}" \
  --set-secrets="${SECRETS}" \
  --add-cloudsql-instances="${CLOUD_SQL_INSTANCE}" \
  --vpc-connector=ai2qa-connector \
  --vpc-egress=private-ranges-only \
  --allow-unauthenticated \
  --quiet

API_URL=$(gcloud run services describe ai2qa-api --region=$REGION --format="value(status.url)")
echo "API URL: $API_URL"

# -----------------------------------------------------------------------------
# Deploy Worker Job (Heavyweight - with browser)
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating Worker Job..."

# Worker env vars
WORKER_ENV_VARS="SPRING_PROFILES_ACTIVE=worker,prod"
WORKER_ENV_VARS="${WORKER_ENV_VARS}@CLOUD_SQL_INSTANCE=${CLOUD_SQL_INSTANCE}"
WORKER_ENV_VARS="${WORKER_ENV_VARS}@GOOGLE_CLOUD_PROJECT=${PROJECT_ID}"
WORKER_ENV_VARS="${WORKER_ENV_VARS}@AI2QA_BROWSER_ENGINE=playwright"
WORKER_ENV_VARS="${WORKER_ENV_VARS}@AI2QA_BROWSER_SNAPSHOT_MODE=aria"

# Add Redis host if configured
if [ -n "$REDIS_HOST" ]; then
  WORKER_ENV_VARS="${WORKER_ENV_VARS}@SPRING_DATA_REDIS_HOST=${REDIS_HOST}"
fi

# Worker secrets (minimal)
WORKER_SECRETS="CLOUD_SQL_PASSWORD=db-password:latest,CACHE_ENCRYPTION_KEY=cache-encryption-key:latest"

gcloud run jobs create ai2qa-worker \
  --image="${DOCKER_REPO}/backend:latest" \
  --region="$REGION" \
  --memory=4Gi \
  --cpu=2 \
  --max-retries=3 \
  --task-timeout=35m \
  --service-account="$SA_EMAIL" \
  --set-env-vars="^@^${WORKER_ENV_VARS}" \
  --set-secrets="${WORKER_SECRETS}" \
  --vpc-connector=ai2qa-connector \
  --vpc-egress=private-ranges-only \
  --quiet 2>/dev/null || \
gcloud run jobs update ai2qa-worker \
  --image="${DOCKER_REPO}/backend:latest" \
  --region="$REGION" \
  --memory=4Gi \
  --cpu=2 \
  --max-retries=3 \
  --task-timeout=35m \
  --service-account="$SA_EMAIL" \
  --set-env-vars="^@^${WORKER_ENV_VARS}" \
  --set-secrets="${WORKER_SECRETS}" \
  --vpc-connector=ai2qa-connector \
  --vpc-egress=private-ranges-only \
  --quiet

echo ""
echo "=== Phase 4 Complete ==="
echo ""
echo "Deployment Summary:"
echo "  API Service: $API_URL"
echo "  Worker Job: ai2qa-worker (invoke with: gcloud run jobs execute ai2qa-worker --region=$REGION)"
echo ""
echo "Test the API:"
echo "  curl $API_URL/actuator/health"
