#!/bin/bash

# =============================================================================
# Ai2QA Local Development Script - Google DeepMind Gemini 3 Hackathon
# No Docker required! Just Java 21 + Node.js + Chrome
# =============================================================================

set -e

echo "🚀 Starting Ai2QA (Hackathon Demo)"
echo "=================================="

# Check prerequisites
if ! command -v java &> /dev/null; then
    echo "❌ Java 21 is required. Please install it first."
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "❌ Node.js is required. Please install it first."
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Java 21+ is required. Found version $JAVA_VERSION"
    exit 1
fi

echo "✅ Java 21 detected"
echo "✅ Node.js detected"

# Build if JAR doesn't exist
if [ ! -f "ai2qa-boot/target/ai2qa-boot-*.jar" ]; then
    echo ""
    echo "📦 Building the application..."
    ./mvnw clean package -DskipTests -q
fi

echo ""
echo "🔧 Configuration:"
echo "   - Database: H2 In-Memory (auto-created)"
echo "   - AI Provider: Google Gemini (Vertex AI)"
echo "   - MCP Server: Bundled"
echo ""

# Check for Google Cloud credentials
if [ -z "$GOOGLE_CLOUD_PROJECT" ]; then
    echo "⚠️  GOOGLE_CLOUD_PROJECT not set. Using default project."
fi

if [ ! -f "$HOME/.config/gcloud/application_default_credentials.json" ]; then
    echo "⚠️  No Google Cloud credentials found."
    echo "   Run: gcloud auth application-default login"
fi

echo "🌐 Starting server on http://localhost:8080"
echo "   H2 Console: http://localhost:8080/h2-console"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Start the application
cd "$(dirname "$0")/.."
java -jar ai2qa-boot/target/ai2qa-boot-*.jar
