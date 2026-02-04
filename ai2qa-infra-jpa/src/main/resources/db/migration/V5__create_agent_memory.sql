-- Global Memory System: The Hippocampus
-- Stores accumulated AI wisdom as a Map<String, String>
-- Key = context_tag, Value = insight_text

CREATE TABLE agent_memory (
    context_tag VARCHAR(255) PRIMARY KEY,     -- The Key (e.g., "framework:react")
    insight_text TEXT NOT NULL,               -- The Value (pipe-delimited wisdom)
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for fast full-table scan during prompt injection
CREATE INDEX idx_agent_memory_updated ON agent_memory(last_updated_at DESC);

-- Comment on table
COMMENT ON TABLE agent_memory IS 'Global AI memory for cross-run learning';
COMMENT ON COLUMN agent_memory.context_tag IS 'Taxonomy key (e.g., framework:react, error:hydration)';
COMMENT ON COLUMN agent_memory.insight_text IS 'Accumulated wisdom, pipe-delimited for nightly compression';
