-- Admin Report Audit Log table for tracking admin access to user reports
-- Required for compliance and security monitoring

CREATE TABLE admin_report_audit_log (
    id UUID PRIMARY KEY,
    admin_user_id VARCHAR(255) NOT NULL,
    test_run_id UUID NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    action_type VARCHAR(20) NOT NULL,  -- VIEW, EXPORT_PDF, EXPORT_EXCEL
    reason VARCHAR(500) NOT NULL,      -- Required justification for access
    client_ip VARCHAR(45),             -- IPv6 max length
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_admin_report_audit_admin ON admin_report_audit_log(admin_user_id);
CREATE INDEX idx_admin_report_audit_test_run ON admin_report_audit_log(test_run_id);
CREATE INDEX idx_admin_report_audit_tenant ON admin_report_audit_log(tenant_id);
CREATE INDEX idx_admin_report_audit_action ON admin_report_audit_log(action_type);
CREATE INDEX idx_admin_report_audit_created ON admin_report_audit_log(created_at);

-- Composite index for admin activity review
CREATE INDEX idx_admin_report_audit_admin_created ON admin_report_audit_log(admin_user_id, created_at DESC);

-- Add comment for documentation
COMMENT ON TABLE admin_report_audit_log IS 'Audit log for admin access to user test reports - tracks all views and exports with required justification';
