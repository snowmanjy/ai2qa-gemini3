# =============================================================================
# Ai2QA Hackathon Dockerfile - Google DeepMind Gemini 3 Hackathon
# Multi-stage build: Maven build + Node.js deps → Slim JRE runtime with Chrome
# Uses H2 in-memory database for zero setup
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build the Spring Boot JAR
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and pom files first (cache layer)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY ai2qa-domain-core/pom.xml ai2qa-domain-core/
COPY ai2qa-application/pom.xml ai2qa-application/
COPY ai2qa-infra-jpa/pom.xml ai2qa-infra-jpa/
COPY ai2qa-mcp-bridge/pom.xml ai2qa-mcp-bridge/
COPY ai2qa-web-api/pom.xml ai2qa-web-api/
COPY ai2qa-boot/pom.xml ai2qa-boot/

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY ai2qa-domain-core/src ai2qa-domain-core/src
COPY ai2qa-application/src ai2qa-application/src
COPY ai2qa-infra-jpa/src ai2qa-infra-jpa/src
COPY ai2qa-mcp-bridge/src ai2qa-mcp-bridge/src
COPY ai2qa-web-api/src ai2qa-web-api/src
COPY ai2qa-boot/src ai2qa-boot/src

# Build the application (skip tests for faster builds)
RUN ./mvnw clean package -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Install MCP Server Node.js dependencies
# -----------------------------------------------------------------------------
FROM node:20-slim AS mcp-builder

WORKDIR /mcp-server

# Copy package files first for better caching
COPY ai2qa-mcp-bridge/src/main/resources/mcp-server/package*.json ./

# Install dependencies (production only, no dev deps)
RUN npm ci --omit=dev

# Copy the server files
COPY ai2qa-mcp-bridge/src/main/resources/mcp-server/*.js ./

# -----------------------------------------------------------------------------
# Stage 3: Runtime image with Chrome & Node.js for MCP
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

# Install dependencies for Chrome, Node.js, and video encoding (ffmpeg)
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    fonts-liberation \
    libasound2t64 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libx11-xcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    xdg-utils \
    bc \
    procps \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Install Google Chrome Stable
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 20 LTS
RUN wget -q -O - https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user with home directory for Chrome
RUN groupadd -r ai2qa && useradd -r -g ai2qa -m -d /home/ai2qa ai2qa

# Create necessary directories for Chrome with proper permissions
RUN mkdir -p /home/ai2qa/.local/share/applications \
    && mkdir -p /home/ai2qa/.cache \
    && chown -R ai2qa:ai2qa /home/ai2qa

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/ai2qa-boot/target/*.jar app.jar

# Copy the MCP server with node_modules from mcp-builder stage
COPY --from=mcp-builder /mcp-server /app/mcp-server

# Copy watchdog script (if exists)
COPY ai2qa-boot/src/main/resources/watchdog.sh /app/watchdog.sh
RUN chmod +x /app/watchdog.sh && chown ai2qa:ai2qa /app/watchdog.sh

# Set ownership
RUN chown -R ai2qa:ai2qa /app

# Switch to non-root user
USER ai2qa

# Cloud Run requires port 8080
EXPOSE 8080

# Environment variables for Chrome headless mode in serverless
ENV CHROME_BIN=/usr/bin/google-chrome-stable
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/google-chrome-stable
ENV PUPPETEER_ARGS="--no-sandbox --disable-gpu --disable-dev-shm-usage --headless=new"
ENV HOME=/home/ai2qa

# MCP Server path - bundled in /app/mcp-server
ENV AI2QA_MCP_SERVER_PATH=/app/mcp-server

# JVM optimizations for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:TieredStopAtLevel=1"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application with watchdog sidecar
ENTRYPOINT ["sh", "-c", "/app/watchdog.sh & java $JAVA_OPTS -jar app.jar"]
