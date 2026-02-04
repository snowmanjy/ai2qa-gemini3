-- V8: Add credit expiration tracking and audit table
-- Purpose: Enable 12-month credit expiration and full audit trail for credit operations

-- Add expiration tracking to tenant table
ALTER TABLE tenant
ADD COLUMN credits_expires_at TIMESTAMP WITH TIME ZONE;

-- Add comment for documentation
COMMENT ON COLUMN tenant.credits_expires_at IS 'When remaining credits expire (12 months from last purchase/grant)';

-- Create audit table for credit operations
CREATE TABLE credit_audit (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    source VARCHAR(30) NOT NULL,
    amount INT NOT NULL,
    old_balance INT NOT NULL,
    new_balance INT NOT NULL,
    performed_by VARCHAR(255),
    reason VARCHAR(500),
    stripe_session_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_credit_source CHECK (
        source IN ('ADMIN_GRANT', 'STRIPE_PURCHASE', 'USAGE_DEDUCT', 'EXPIRATION')
    )
);

-- Add comments for documentation
COMMENT ON TABLE credit_audit IS 'Audit trail for all credit operations';
COMMENT ON COLUMN credit_audit.source IS 'Credit source: ADMIN_GRANT (admin API), STRIPE_PURCHASE (checkout), USAGE_DEDUCT (test run), EXPIRATION (TTL job)';
COMMENT ON COLUMN credit_audit.performed_by IS 'Tenant ID of admin who performed grant (null for system/stripe operations)';
COMMENT ON COLUMN credit_audit.reason IS 'Human-readable reason for the operation';
COMMENT ON COLUMN credit_audit.stripe_session_id IS 'Stripe checkout session ID (for purchases only)';

-- Index for tenant lookup (most common query pattern)
CREATE INDEX idx_credit_audit_tenant_id ON credit_audit(tenant_id);

-- Index for finding tenants with expired credits efficiently
CREATE INDEX idx_tenant_credits_expires ON tenant(credits_expires_at)
    WHERE credits_balance > 0;

-- Index for audit queries by date range
CREATE INDEX idx_credit_audit_created_at ON credit_audit(created_at);
