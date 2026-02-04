-- Phase 6: Hippocampus Expansion - Structured Knowledge Base
-- Stores site-specific patterns, selectors, and flow strategies for QA learning

-- 1. Site-specific patterns and quirks
CREATE TABLE qa_site_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain VARCHAR(255) NOT NULL,              -- e.g., "app.example.com"
    pattern_type VARCHAR(50) NOT NULL,         -- 'SELECTOR', 'TIMING', 'AUTH', 'QUIRK'
    pattern_key VARCHAR(255) NOT NULL,         -- e.g., "login_button", "modal_close"
    pattern_value TEXT NOT NULL,               -- The actual selector or config
    confidence_score DECIMAL(3,2) DEFAULT 0.50, -- 0.00-1.00
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    avg_duration_ms INT,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by_run_id UUID,                    -- Which test run discovered this
    visibility VARCHAR(20) DEFAULT 'GLOBAL',   -- 'GLOBAL', 'TENANT', 'PRIVATE'
    tenant_id VARCHAR(255),                    -- Only if visibility = 'TENANT' or 'PRIVATE'
    version INT DEFAULT 0,                     -- Optimistic locking

    CONSTRAINT uq_site_pattern UNIQUE(domain, pattern_type, pattern_key, tenant_id)
);

-- 2. Selector alternatives (multiple ways to find same element)
CREATE TABLE qa_selector_alternatives (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_pattern_id UUID NOT NULL REFERENCES qa_site_patterns(id) ON DELETE CASCADE,
    selector_type VARCHAR(20) NOT NULL,        -- 'CSS', 'XPATH', 'TEXT', 'ARIA', 'DATA_TESTID'
    selector_value TEXT NOT NULL,
    priority INT DEFAULT 0,                    -- Higher = try first
    success_rate DECIMAL(3,2) DEFAULT 0.50,
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Test flow strategies (how to accomplish common tasks)
CREATE TABLE qa_flow_strategies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain VARCHAR(255),                       -- NULL = generic strategy
    flow_name VARCHAR(100) NOT NULL,           -- e.g., "login", "checkout", "search"
    description TEXT,
    steps_json JSONB NOT NULL,                 -- Array of steps with selectors
    success_count INT DEFAULT 0,
    failure_count INT DEFAULT 0,
    avg_duration_ms INT,
    visibility VARCHAR(20) DEFAULT 'GLOBAL',
    tenant_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    version INT DEFAULT 0                      -- Optimistic locking
);

-- 4. Framework detection patterns
CREATE TABLE qa_framework_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    framework VARCHAR(50) NOT NULL,            -- 'react', 'angular', 'vue', etc.
    detection_pattern TEXT NOT NULL,           -- HTML/JS pattern to detect
    associated_quirks JSONB,                   -- Known quirks for this framework
    recommended_waits JSONB,                   -- Timing recommendations
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 5. Knowledge access metering (for future billing - Phase 7)
CREATE TABLE qa_knowledge_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    access_type VARCHAR(20) NOT NULL,          -- 'RENT', 'LEARN'
    domain VARCHAR(255),
    patterns_accessed INT DEFAULT 0,
    patterns_contributed INT DEFAULT 0,
    credits_charged DECIMAL(10,4) DEFAULT 0,
    accessed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for efficient queries
CREATE INDEX idx_site_patterns_domain ON qa_site_patterns(domain);
CREATE INDEX idx_site_patterns_type ON qa_site_patterns(pattern_type);
CREATE INDEX idx_site_patterns_visibility ON qa_site_patterns(visibility);
CREATE INDEX idx_site_patterns_tenant ON qa_site_patterns(tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_site_patterns_confidence ON qa_site_patterns(confidence_score DESC);

CREATE INDEX idx_selector_alt_pattern ON qa_selector_alternatives(site_pattern_id);
CREATE INDEX idx_selector_alt_priority ON qa_selector_alternatives(priority DESC);

CREATE INDEX idx_flow_strategies_domain ON qa_flow_strategies(domain);
CREATE INDEX idx_flow_strategies_name ON qa_flow_strategies(flow_name);
CREATE INDEX idx_flow_strategies_visibility ON qa_flow_strategies(visibility);

CREATE INDEX idx_framework_sig_framework ON qa_framework_signatures(framework);

CREATE INDEX idx_knowledge_access_tenant ON qa_knowledge_access_log(tenant_id, accessed_at);
CREATE INDEX idx_knowledge_access_domain ON qa_knowledge_access_log(domain) WHERE domain IS NOT NULL;

-- Comments for documentation
COMMENT ON TABLE qa_site_patterns IS 'Site-specific QA patterns discovered from test runs';
COMMENT ON TABLE qa_selector_alternatives IS 'Alternative selectors for the same element';
COMMENT ON TABLE qa_flow_strategies IS 'Reusable test flow strategies for common tasks';
COMMENT ON TABLE qa_framework_signatures IS 'Framework detection patterns and associated quirks';
COMMENT ON TABLE qa_knowledge_access_log IS 'Metering log for knowledge access (rent/learn)';

COMMENT ON COLUMN qa_site_patterns.pattern_type IS 'SELECTOR, TIMING, AUTH, or QUIRK';
COMMENT ON COLUMN qa_site_patterns.visibility IS 'GLOBAL (shared), TENANT (org-specific), or PRIVATE';
COMMENT ON COLUMN qa_selector_alternatives.selector_type IS 'CSS, XPATH, TEXT, ARIA, or DATA_TESTID';
COMMENT ON COLUMN qa_flow_strategies.steps_json IS 'JSON array of FlowStep objects with action, selector, etc.';
