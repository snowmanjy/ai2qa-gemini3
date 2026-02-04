-- V16: Add unique constraint on stripe_session_id for idempotency
-- Purpose: Prevent duplicate credit additions from Stripe webhook retries
-- Note: PostgreSQL allows multiple NULL values with unique constraint,
--       so only STRIPE_PURCHASE entries (with non-null session ID) are affected

-- First, remove duplicate stripe_session_id entries (keep only the first one by created_at)
DELETE FROM credit_audit ca1
WHERE ca1.stripe_session_id IS NOT NULL
  AND ca1.id NOT IN (
    SELECT DISTINCT ON (stripe_session_id) id
    FROM credit_audit
    WHERE stripe_session_id IS NOT NULL
    ORDER BY stripe_session_id, created_at ASC
  );

-- Create unique index on stripe_session_id (only for non-null values)
CREATE UNIQUE INDEX idx_credit_audit_stripe_session_unique
    ON credit_audit(stripe_session_id)
    WHERE stripe_session_id IS NOT NULL;

COMMENT ON INDEX idx_credit_audit_stripe_session_unique IS 'Ensures idempotency for Stripe webhook processing - prevents duplicate credit additions';
