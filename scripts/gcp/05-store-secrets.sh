#!/bin/bash
# =============================================================================
# GCP Infrastructure Setup - Phase 6: Store Production Secrets
# =============================================================================
# Hackathon Edition: Simplified secrets (no Clerk, Stripe, PostHog, Anthropic)
# =============================================================================

set -e

export PROJECT_ID="${PROJECT_ID:-ai2qa-484417}"

echo "=== Phase 6: Store Production Secrets (Hackathon Edition) ==="
echo ""
echo "This script will create SECRET PLACEHOLDERS in Secret Manager."
echo "You MUST update them with real values before deployment."
echo ""

# Function to create or update secret
create_secret() {
  local name=$1
  local value=$2
  local description=$3

  echo ">>> Creating secret: $name ($description)"
  printf "%s" "$value" | gcloud secrets create $name --data-file=- --quiet 2>/dev/null || \
    printf "%s" "$value" | gcloud secrets versions add $name --data-file=-
}

# Database password was created in 03-create-infrastructure.sh
echo ">>> db-password: Already created during infrastructure setup"

# Email (optional - for notifications)
create_secret "gmail-app-password" "PLACEHOLDER" "Gmail App Password (16 chars) - Optional"

# Cache encryption
CACHE_KEY=$(openssl rand -base64 32)
create_secret "cache-encryption-key" "$CACHE_KEY" "Cache Encryption Key (auto-generated)"

# Google Safe Browsing (optional URL safety check)
create_secret "google-safe-browsing-api-key" "PLACEHOLDER" "Google Safe Browsing API Key - Optional"

# reCAPTCHA (optional - for abuse prevention)
create_secret "recaptcha-secret-key" "PLACEHOLDER" "Google reCAPTCHA v3 Secret Key - Optional"

echo ""
echo "=== Secrets Created (Hackathon Edition) ==="
echo ""
echo "Required secrets:"
echo "  - db-password (auto-created during infrastructure setup)"
echo "  - cache-encryption-key (auto-generated)"
echo ""
echo "Optional secrets (update if needed):"
echo "  gcloud secrets versions add gmail-app-password --data-file=-"
echo "  gcloud secrets versions add google-safe-browsing-api-key --data-file=-"
echo "  gcloud secrets versions add recaptcha-secret-key --data-file=-"
echo ""
echo "Example:"
echo "  echo -n 'your-app-password' | gcloud secrets versions add gmail-app-password --data-file=-"
echo ""
echo "Next: Deploy the application with 06-deploy.sh"
