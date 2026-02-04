-- Test run table for storing execution history
-- Persona column added in V3 migration

CREATE TABLE test_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    target_url TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    goals JSONB NOT NULL DEFAULT '[]'::jsonb,
    plan JSONB NOT NULL DEFAULT '[]'::jsonb,
    executed_steps JSONB NOT NULL DEFAULT '[]'::jsonb,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_test_run_tenant_id ON test_run(tenant_id);
CREATE INDEX idx_test_run_tenant_status ON test_run(tenant_id, status);
CREATE INDEX idx_test_run_created_at ON test_run(created_at);

COMMENT ON TABLE test_run IS 'Test run execution records';
COMMENT ON COLUMN test_run.tenant_id IS 'External tenant identifier from JWT';
COMMENT ON COLUMN test_run.goals IS 'User-provided test goals (JSON array)';
COMMENT ON COLUMN test_run.plan IS 'Planned steps for the run (JSON array)';
COMMENT ON COLUMN test_run.executed_steps IS 'Executed steps and outcomes (JSON array)';
