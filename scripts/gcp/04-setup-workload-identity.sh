#!/bin/bash
# =============================================================================
# GCP Infrastructure Setup - Phase 3: Workload Identity Federation for GitHub
# No service account keys needed - GitHub Actions authenticates via OIDC
# =============================================================================

set -e

export PROJECT_ID="${PROJECT_ID:-ai2qa-484417}"
export REGION="${REGION:-us-central1}"
export GITHUB_ORG="${GITHUB_ORG:-SameThoughts}"
export GITHUB_REPO="${GITHUB_REPO:-AI2QA}"

echo "=== Phase 3: Workload Identity Federation ==="
echo "GitHub Repo: $GITHUB_ORG/$GITHUB_REPO"

# Get project number
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
echo "Project Number: $PROJECT_NUMBER"

# -----------------------------------------------------------------------------
# 3.1 Create Workload Identity Pool
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating Workload Identity Pool..."
gcloud iam workload-identity-pools create "github-pool" \
  --location="global" \
  --display-name="GitHub Actions Pool" \
  --quiet 2>/dev/null || echo "Pool may already exist, continuing..."

# -----------------------------------------------------------------------------
# 3.2 Create OIDC Provider
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating OIDC Provider..."
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --quiet 2>/dev/null || echo "Provider may already exist, continuing..."

# -----------------------------------------------------------------------------
# 3.3 Create GitHub Actions Service Account
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating GitHub Actions service account..."
gcloud iam service-accounts create github-actions \
  --display-name="GitHub Actions CI/CD" \
  --quiet 2>/dev/null || echo "Service account may already exist, continuing..."

export GITHUB_SA="github-actions@${PROJECT_ID}.iam.gserviceaccount.com"
echo "GitHub SA: $GITHUB_SA"

# Grant deployment permissions
echo ""
echo ">>> Granting deployment permissions..."
ROLES=(
  "roles/run.admin"
  "roles/run.invoker"
  "roles/run.developer"
  "roles/artifactregistry.writer"
  "roles/artifactregistry.repoAdmin"
  "roles/iam.serviceAccountUser"
  "roles/storage.admin"
  "roles/storage.objectAdmin"
  "roles/cloudbuild.builds.builder"
  "roles/cloudbuild.builds.viewer"
  "roles/secretmanager.secretAccessor"
  "roles/logging.logWriter"
  "roles/logging.viewer"
  "roles/firebase.admin"
  "roles/firebasehosting.admin"
)

for ROLE in "${ROLES[@]}"; do
  echo "  - Granting $ROLE"
  gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$GITHUB_SA" \
    --role="$ROLE" \
    --quiet > /dev/null
done

# Grant repository-level permissions for Artifact Registry
echo ""
echo ">>> Granting Artifact Registry repository-level permissions..."
gcloud artifacts repositories add-iam-policy-binding ai2qa \
  --location=$REGION \
  --member="serviceAccount:$GITHUB_SA" \
  --role="roles/artifactregistry.repoAdmin" \
  --quiet > /dev/null 2>&1 || true

# -----------------------------------------------------------------------------
# 3.4 Allow GitHub to impersonate service account
# -----------------------------------------------------------------------------
echo ""
echo ">>> Configuring workload identity binding..."
gcloud iam service-accounts add-iam-policy-binding $GITHUB_SA \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${GITHUB_ORG}/${GITHUB_REPO}" \
  --quiet

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
echo ""
echo "=== Phase 3 Complete ==="
echo ""
echo "GitHub Actions Configuration:"
echo ""
echo "Add these secrets to your GitHub repository:"
echo "  GCP_PROJECT_ID: $PROJECT_ID"
echo "  GCP_REGION: $REGION"
echo "  GCP_WORKLOAD_IDENTITY_PROVIDER: projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/providers/github-provider"
echo "  GCP_SERVICE_ACCOUNT: $GITHUB_SA"
echo ""
echo "Next: Run 05-store-secrets.sh"
