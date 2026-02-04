#!/bin/bash
# Local development runner - loads environment variables automatically

# Load from .env.local file if it exists
if [ -f .env.local ]; then
    echo "Loading environment from .env.local..."
    set -a  # automatically export all variables
    source .env.local
    set +a
    echo "✓ Loaded environment from .env.local"

    # Debug: show key env vars are set (without revealing values)
    echo "  OPENAI_API_KEY: ${OPENAI_API_KEY:+[SET]}"
    echo "  AI_PROVIDER: $AI_PROVIDER"
    echo "  GOOGLE_CLOUD_PROJECT: $GOOGLE_CLOUD_PROJECT"
    echo "  BROWSER_ENGINE: ${BROWSER_ENGINE:-puppeteer}"
fi

# Run the backend
echo ""
echo "Starting AI2QA backend..."
mvn clean install -Dmaven.test.skip=true
mvn spring-boot:run -pl ai2qa-boot
