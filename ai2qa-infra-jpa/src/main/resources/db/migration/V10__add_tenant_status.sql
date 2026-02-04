-- Add status column to tenant table for soft-delete functionality
-- ACTIVE: Tenant can use the system normally
-- DISABLED: Tenant is soft-deleted and cannot run tests (for audit purposes)

ALTER TABLE tenant ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Add check constraint for valid status values
ALTER TABLE tenant ADD CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'DISABLED'));

-- Index for filtering by status
CREATE INDEX idx_tenant_status ON tenant(status);

-- Comments
COMMENT ON COLUMN tenant.status IS 'Tenant account status: ACTIVE or DISABLED (soft-delete)';
