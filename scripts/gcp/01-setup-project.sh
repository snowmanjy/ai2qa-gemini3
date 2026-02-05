#!/bin/bash
# =============================================================================
# GCP Infrastructure Setup - Phase 1: Project Foundation
# =============================================================================
# Prerequisites:
#   - gcloud CLI installed and authenticated
#   - Billing account linked to project
#   - Run: gcloud auth login
# =============================================================================

set -e

# Configuration - Hackathon Demo Environment
export PROJECT_ID="${PROJECT_ID:-ai2qa-hackathon-demo}"
export REGION="${REGION:-us-central1}"

echo "=== Phase 1: GCP Project Foundation ==="
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo ""

# Set default project
echo ">>> Setting default project..."
gcloud config set project $PROJECT_ID

# Enable required APIs
echo ">>> Enabling required APIs (this may take a few minutes)..."
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  aiplatform.googleapis.com \
  iam.googleapis.com

echo ""
echo "=== Phase 1 Complete ==="
echo "Next: Run 02-create-service-account.sh"
