# =============================================================================
# Next.js Frontend Dockerfile - Optimized for Cloud Run
# =============================================================================

FROM node:20-alpine AS base

# Install dependencies only when needed
FROM base AS deps
WORKDIR /app

# Copy package files
COPY package.json package-lock.json* ./

# Install dependencies
RUN npm ci

# Rebuild the source code only when needed
FROM base AS builder
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build arguments for environment variables (set at build time)
ARG NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_APP_URL
ARG NEXT_PUBLIC_POSTHOG_KEY
ARG NEXT_PUBLIC_POSTHOG_HOST
ARG NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY
ARG STRIPE_SINGLE_RUN_PRICE_ID
ARG STRIPE_5_RUN_PRICE_ID
ARG STRIPE_10_RUN_PRICE_ID
ARG STRIPE_30_RUN_PRICE_ID
ARG STRIPE_60_RUN_PRICE_ID

# Set environment variables for build
ENV NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY=$NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_APP_URL=$NEXT_PUBLIC_APP_URL
ENV NEXT_PUBLIC_POSTHOG_KEY=$NEXT_PUBLIC_POSTHOG_KEY
ENV NEXT_PUBLIC_POSTHOG_HOST=$NEXT_PUBLIC_POSTHOG_HOST
ENV NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=$NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY
ENV STRIPE_SINGLE_RUN_PRICE_ID=$STRIPE_SINGLE_RUN_PRICE_ID
ENV STRIPE_5_RUN_PRICE_ID=$STRIPE_5_RUN_PRICE_ID
ENV STRIPE_10_RUN_PRICE_ID=$STRIPE_10_RUN_PRICE_ID
ENV STRIPE_30_RUN_PRICE_ID=$STRIPE_30_RUN_PRICE_ID
ENV STRIPE_60_RUN_PRICE_ID=$STRIPE_60_RUN_PRICE_ID

# Build Next.js
RUN npm run build

# Production image, copy all the files and run next
FROM base AS runner
WORKDIR /app

ENV NODE_ENV=production

# Create non-root user
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Copy built assets
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
