#!/bin/bash
# =============================================================================
# GCP Infrastructure Setup - Phase 1.3: Create Application Service Account
# =============================================================================

set -e

export PROJECT_ID="${PROJECT_ID:-ai2qa-hackathon-demo}"
export REGION="${REGION:-us-central1}"

echo "=== Creating Application Service Account ==="

# Create service account
echo ">>> Creating ai2qa-app service account..."
gcloud iam service-accounts create ai2qa-app \
  --display-name="AI2QA Application" \
  --quiet 2>/dev/null || echo "Service account may already exist, continuing..."

export SA_EMAIL="ai2qa-app@${PROJECT_ID}.iam.gserviceaccount.com"
echo "Service Account: $SA_EMAIL"

# Grant required roles
echo ">>> Granting IAM roles..."
ROLES=(
  "roles/cloudsql.client"
  "roles/storage.objectAdmin"
  "roles/aiplatform.user"
  "roles/secretmanager.secretAccessor"
  "roles/logging.logWriter"
  "roles/cloudtrace.agent"
  "roles/errorreporting.writer"
)

for ROLE in "${ROLES[@]}"; do
  echo "  - Granting $ROLE"
  gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SA_EMAIL" \
    --role="$ROLE" \
    --quiet > /dev/null
done

echo ""
echo "=== Service Account Created ==="
echo "SA_EMAIL=$SA_EMAIL"
echo ""
echo "Next: Run 03-create-infrastructure.sh"
