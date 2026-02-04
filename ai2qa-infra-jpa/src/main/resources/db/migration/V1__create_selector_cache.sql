-- Semantic Cache for selector reuse
-- Stores successful selectors per tenant/goal/url combination

CREATE TABLE selector_cache (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    goal_hash VARCHAR(64) NOT NULL,          -- SHA-256 of goal text
    url_pattern VARCHAR(512) NOT NULL,        -- URL pattern (may include wildcards)
    selector_value BYTEA NOT NULL,            -- AES-256 encrypted selector
    element_description VARCHAR(512),         -- Human-readable description
    success_count INT DEFAULT 1,
    failure_count INT DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_selector_cache_tenant_goal_url UNIQUE(tenant_id, goal_hash, url_pattern)
);

-- Index for fast lookups
CREATE INDEX idx_selector_cache_lookup ON selector_cache(tenant_id, goal_hash, url_pattern);

-- Index for cleanup of stale entries
CREATE INDEX idx_selector_cache_last_used ON selector_cache(last_used_at);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_selector_cache_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_selector_cache_updated_at
    BEFORE UPDATE ON selector_cache
    FOR EACH ROW
    EXECUTE FUNCTION update_selector_cache_updated_at();

-- Comment on table
COMMENT ON TABLE selector_cache IS 'Caches successful selectors to reduce Gemini API calls';
COMMENT ON COLUMN selector_cache.goal_hash IS 'SHA-256 hash of the goal text for lookup';
COMMENT ON COLUMN selector_cache.selector_value IS 'AES-256 encrypted CSS/XPath selector';
COMMENT ON COLUMN selector_cache.success_count IS 'Number of successful uses of this selector';
COMMENT ON COLUMN selector_cache.failure_count IS 'Number of failed attempts with this selector';
