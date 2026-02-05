#!/bin/bash
# =============================================================================
# GCP Infrastructure Setup - Deploy Frontend to Cloud Run
# =============================================================================
# Hackathon Edition: No Clerk/PostHog/Stripe
# =============================================================================

set -e

export PROJECT_ID="${PROJECT_ID:-ai2qa-hackathon-demo}"
export REGION="${REGION:-us-central1}"
export DOCKER_REPO="${REGION}-docker.pkg.dev/${PROJECT_ID}/ai2qa"

echo "=== Deploy Frontend to Cloud Run (Hackathon Edition) ==="

cd "$(dirname "$0")/../../frontend"

# Configure Docker for Artifact Registry
gcloud auth configure-docker ${REGION}-docker.pkg.dev --quiet

# Build Docker image with build args
echo ">>> Building frontend Docker image..."
docker build \
  --build-arg NEXT_PUBLIC_API_URL="https://ai2qa-api-${PROJECT_ID}.a.run.app" \
  --build-arg NEXT_PUBLIC_APP_URL="${NEXT_PUBLIC_APP_URL:-https://ai2qa.com}" \
  --build-arg NEXT_PUBLIC_RECAPTCHA_SITE_KEY="6LcrDGEsAAAAAK_qxNiXTZSazlwvrcpM4pJt92Gy" \
  -t ${DOCKER_REPO}/frontend:latest \
  .

echo ">>> Pushing to Artifact Registry..."
docker push ${DOCKER_REPO}/frontend:latest

echo ">>> Deploying to Cloud Run..."
gcloud run deploy ai2qa-frontend \
  --image=${DOCKER_REPO}/frontend:latest \
  --region=$REGION \
  --memory=512Mi \
  --cpu=1 \
  --min-instances=0 \
  --max-instances=5 \
  --concurrency=80 \
  --timeout=60s \
  --service-account=ai2qa-app@${PROJECT_ID}.iam.gserviceaccount.com \
  --set-env-vars="NODE_ENV=production" \
  --allow-unauthenticated \
  --quiet

FRONTEND_URL=$(gcloud run services describe ai2qa-frontend --region=$REGION --format='value(status.url)')

echo ""
echo "=== Frontend Deployed ==="
echo "URL: $FRONTEND_URL"
echo ""
echo "To use a custom domain, run:"
echo "  gcloud run domain-mappings create --service=ai2qa-frontend --domain=ai2qa.com --region=$REGION"
