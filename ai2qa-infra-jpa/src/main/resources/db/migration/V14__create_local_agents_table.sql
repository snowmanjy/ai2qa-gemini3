-- Create local_agents table for storing user's local browser automation agents
CREATE TABLE local_agents (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    device_name VARCHAR(255) NOT NULL,
    device_token_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_local_agents_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id)
        ON DELETE CASCADE
);

-- Index for finding agents by tenant
CREATE INDEX idx_local_agents_tenant_id ON local_agents(tenant_id);

-- Index for finding agents by token hash (used during WebSocket auth)
CREATE INDEX idx_local_agents_token_hash ON local_agents(device_token_hash);

-- Index for finding online agents
CREATE INDEX idx_local_agents_status ON local_agents(tenant_id, status);

-- Add execution_mode to test_runs table
ALTER TABLE test_run ADD COLUMN execution_mode VARCHAR(20) DEFAULT 'CLOUD';
ALTER TABLE test_run ADD COLUMN agent_id UUID REFERENCES local_agents(id);
