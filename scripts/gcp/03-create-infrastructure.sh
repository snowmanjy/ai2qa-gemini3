#!/bin/bash
# =============================================================================
# GCP Infrastructure Setup - Phase 2: Core Infrastructure
# VPC, Cloud SQL, Storage, Artifact Registry
# =============================================================================

set -e

export PROJECT_ID="${PROJECT_ID:-ai2qa-hackathon-demo}"
export REGION="${REGION:-us-central1}"

echo "=== Phase 2: Core Infrastructure ==="

# -----------------------------------------------------------------------------
# 2.1 VPC Network
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating VPC Network..."
gcloud compute networks create ai2qa-demo-vpc \
  --subnet-mode=custom \
  --quiet 2>/dev/null || echo "VPC may already exist, continuing..."

echo ">>> Creating Subnet..."
gcloud compute networks subnets create ai2qa-demo-subnet \
  --network=ai2qa-demo-vpc \
  --region=$REGION \
  --range=10.0.0.0/24 \
  --enable-private-ip-google-access \
  --quiet 2>/dev/null || echo "Subnet may already exist, continuing..."

# -----------------------------------------------------------------------------
# 2.2 VPC Connector for Cloud Run (serverless VPC access)
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating Serverless VPC Connector..."
gcloud compute networks vpc-access connectors create ai2qa-demo-connector \
  --region=$REGION \
  --network=ai2qa-demo-vpc \
  --range=10.8.0.0/28 \
  --min-instances=2 \
  --max-instances=3 \
  --machine-type=f1-micro \
  --quiet 2>/dev/null || echo "VPC Connector may already exist, continuing..."

# -----------------------------------------------------------------------------
# 2.3 Cloud SQL (PostgreSQL)
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating Cloud SQL Instance (this takes 5-10 minutes)..."

# Generate secure password
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '=+/' | head -c 24)

# Check if instance exists
if gcloud sql instances describe ai2qa-db --quiet 2>/dev/null; then
  echo "Cloud SQL instance already exists, skipping creation..."
else
  gcloud sql instances create ai2qa-demo-db \
    --database-version=POSTGRES_15 \
    --tier=db-f1-micro \
    --region=$REGION \
    --storage-size=10GB \
    --storage-auto-increase \
    --backup-start-time=02:00 \
    --availability-type=zonal \
    --quiet

  # Create database and user
  echo ">>> Creating database and user..."
  gcloud sql databases create ai2qa --instance=ai2qa-demo-db --quiet 2>/dev/null || true
  gcloud sql users create ai2qa --instance=ai2qa-demo-db --password="$DB_PASSWORD" --quiet 2>/dev/null || true

  # Store password in Secret Manager
  echo ">>> Storing database password in Secret Manager..."
  printf "%s" "$DB_PASSWORD" | gcloud secrets create db-password --data-file=- --quiet 2>/dev/null || \
    printf "%s" "$DB_PASSWORD" | gcloud secrets versions add db-password --data-file=-

  echo ""
  echo "!!! IMPORTANT: Database password stored in Secret Manager as 'db-password' !!!"
  echo "!!! Save this password securely: $DB_PASSWORD !!!"
fi

# Get Cloud SQL connection name
CLOUD_SQL_INSTANCE=$(gcloud sql instances describe ai2qa-demo-db --format="value(connectionName)")
echo "Cloud SQL Instance: $CLOUD_SQL_INSTANCE"

# -----------------------------------------------------------------------------
# 2.4 Cloud Storage
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating Cloud Storage Bucket..."
gcloud storage buckets create gs://${PROJECT_ID}-artifacts \
  --location=$REGION \
  --uniform-bucket-level-access \
  --quiet 2>/dev/null || echo "Bucket may already exist, continuing..."

# -----------------------------------------------------------------------------
# 2.5 Artifact Registry
# -----------------------------------------------------------------------------
echo ""
echo ">>> Creating Artifact Registry..."
gcloud artifacts repositories create ai2qa \
  --repository-format=docker \
  --location=$REGION \
  --description="AI2QA Docker images" \
  --quiet 2>/dev/null || echo "Repository may already exist, continuing..."

# -----------------------------------------------------------------------------
# 2.6 Grant ALL required IAM permissions (comprehensive)
# -----------------------------------------------------------------------------
echo ""
echo ">>> Granting ALL required IAM permissions..."
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)")
SA_EMAIL="ai2qa-app@${PROJECT_ID}.iam.gserviceaccount.com"

# All Cloud Build service accounts
CLOUD_BUILD_SAS=(
  "${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com"
  "${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
  "service-${PROJECT_NUMBER}@gcp-sa-cloudbuild.iam.gserviceaccount.com"
)

echo "  Setting up Cloud Build service accounts..."
for SA in "${CLOUD_BUILD_SAS[@]}"; do
  # Project-level permissions
  for ROLE in "roles/storage.objectAdmin" "roles/logging.logWriter" "roles/artifactregistry.writer" "roles/run.admin" "roles/iam.serviceAccountUser"; do
    gcloud projects add-iam-policy-binding $PROJECT_ID \
      --member="serviceAccount:$SA" \
      --role="$ROLE" \
      --quiet > /dev/null 2>&1 || true
  done
  # Repository-level artifact registry
  gcloud artifacts repositories add-iam-policy-binding ai2qa \
    --location=$REGION \
    --member="serviceAccount:$SA" \
    --role="roles/artifactregistry.writer" \
    --quiet > /dev/null 2>&1 || true
done
echo "    ✓ Cloud Build SAs configured"

# Serverless robot needs to pull images
echo "  Setting up Cloud Run serverless robot..."
gcloud artifacts repositories add-iam-policy-binding ai2qa \
  --location=$REGION \
  --member="serviceAccount:service-${PROJECT_NUMBER}@serverless-robot-prod.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.reader" \
  --quiet > /dev/null 2>&1 || true
echo "    ✓ Serverless robot configured"

# App service account needs these roles
echo "  Setting up application service account..."
APP_ROLES=(
  "roles/cloudsql.client"
  "roles/storage.objectAdmin"
  "roles/secretmanager.secretAccessor"
  "roles/logging.logWriter"
  "roles/cloudtrace.agent"
  "roles/errorreporting.writer"
  "roles/run.invoker"
  "roles/run.developer"
)
for ROLE in "${APP_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SA_EMAIL" \
    --role="$ROLE" \
    --quiet > /dev/null 2>&1 || true
done
echo "    ✓ Application SA configured"

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
echo ""
echo "=== Phase 2 Complete ==="
echo ""
echo "Infrastructure Summary:"
echo "  VPC Network: ai2qa-vpc"
echo "  VPC Connector: ai2qa-connector"
echo "  Cloud SQL Instance: $CLOUD_SQL_INSTANCE"
echo "  Storage Bucket: gs://${PROJECT_ID}-artifacts"
echo "  Artifact Registry: ${REGION}-docker.pkg.dev/${PROJECT_ID}/ai2qa"
echo ""
echo "IAM Permissions Configured:"
echo "  Cloud Build SAs: storage, logging, artifactregistry, run.admin, iam.serviceAccountUser"
echo "  Serverless Robot: artifactregistry.reader"
echo "  Application SA: cloudsql, storage, secrets, logging, tracing, run"
echo ""
echo "Next: Run 04-setup-workload-identity.sh"
