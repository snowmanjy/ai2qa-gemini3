-- Create saved_test_plan table for storing reusable test plans
-- Users can save test configurations (URL, goals, persona) and run them later

CREATE TABLE saved_test_plan (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    target_url VARCHAR(2048) NOT NULL,
    goals JSONB NOT NULL,
    persona VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for tenant queries (most common access pattern)
CREATE INDEX idx_saved_test_plan_tenant_id ON saved_test_plan(tenant_id);

-- Unique constraint: plan names must be unique per tenant
CREATE UNIQUE INDEX idx_saved_test_plan_name_tenant ON saved_test_plan(tenant_id, name);

COMMENT ON TABLE saved_test_plan IS 'Stores reusable test plan configurations that users can run multiple times';
COMMENT ON COLUMN saved_test_plan.name IS 'User-defined name for the plan (unique per tenant)';
COMMENT ON COLUMN saved_test_plan.description IS 'Optional description of what the plan tests';
COMMENT ON COLUMN saved_test_plan.goals IS 'JSON array of test objectives';
COMMENT ON COLUMN saved_test_plan.persona IS 'Testing strategy: STANDARD, CHAOS, or HACKER';
