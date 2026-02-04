-- Security Audit Log table for tracking URL validation decisions
-- This provides visibility into potential attack attempts and security events

CREATE TABLE security_audit_log (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255),
    client_ip VARCHAR(45),  -- IPv6 max length
    target_url VARCHAR(2048),
    target_domain VARCHAR(255),
    decision VARCHAR(20) NOT NULL,  -- ALLOWED, BLOCKED, RATE_LIMITED
    block_reason VARCHAR(500),
    risk_score INTEGER DEFAULT 0,
    user_agent VARCHAR(500),
    request_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_security_audit_tenant ON security_audit_log(tenant_id);
CREATE INDEX idx_security_audit_ip ON security_audit_log(client_ip);
CREATE INDEX idx_security_audit_domain ON security_audit_log(target_domain);
CREATE INDEX idx_security_audit_decision ON security_audit_log(decision);
CREATE INDEX idx_security_audit_created ON security_audit_log(created_at);

-- Composite index for dashboard queries
CREATE INDEX idx_security_audit_tenant_created ON security_audit_log(tenant_id, created_at DESC);

-- Add comment for documentation
COMMENT ON TABLE security_audit_log IS 'Audit log for URL validation decisions - tracks allowed, blocked, and rate-limited requests for security monitoring';
