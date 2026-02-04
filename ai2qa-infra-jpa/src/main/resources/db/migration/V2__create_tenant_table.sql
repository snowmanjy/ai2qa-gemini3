-- Tenant table for subscription management and credits
-- Stores tenant plan and credit balance for metering

CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    current_plan VARCHAR(50) NOT NULL DEFAULT 'FREE_TRIAL',
    credits_balance INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_tenant_plan CHECK (current_plan IN ('FREE_TRIAL', 'STARTER', 'GROWTH', 'SCALE')),
    CONSTRAINT chk_tenant_credits CHECK (credits_balance >= 0)
);

-- Index for fast lookups by tenant_id (used in every request)
CREATE INDEX idx_tenant_tenant_id ON tenant(tenant_id);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_tenant_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_tenant_updated_at
    BEFORE UPDATE ON tenant
    FOR EACH ROW
    EXECUTE FUNCTION update_tenant_updated_at();

-- Comments
COMMENT ON TABLE tenant IS 'Tenant subscription and credits management';
COMMENT ON COLUMN tenant.tenant_id IS 'External tenant identifier from JWT';
COMMENT ON COLUMN tenant.current_plan IS 'Subscription tier: FREE_TRIAL, STARTER, GROWTH, SCALE';
COMMENT ON COLUMN tenant.credits_balance IS 'Available test run credits';
COMMENT ON COLUMN tenant.version IS 'Optimistic locking version for concurrent updates';
