-- V10: Create AI model metrics table for tracking model performance
-- Purpose: Enable data-driven AI model optimization and fallback strategy analysis

CREATE TABLE ai_model_metrics (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    test_run_id UUID,
    model_provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(100),
    operation_type VARCHAR(30) NOT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT true,
    fallback_used BOOLEAN NOT NULL DEFAULT false,
    error_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_operation_type CHECK (
        operation_type IN ('ELEMENT_FIND', 'PLAN_GENERATION', 'REPAIR_PLAN', 'REFLECTION')
    )
);

-- Comments for documentation
COMMENT ON TABLE ai_model_metrics IS 'Metrics for AI model operations - tracks performance, cost, and accuracy';
COMMENT ON COLUMN ai_model_metrics.model_provider IS 'AI provider: vertex-ai, anthropic, openai';
COMMENT ON COLUMN ai_model_metrics.model_name IS 'Specific model: gemini-2.0-flash, claude-sonnet-4, etc.';
COMMENT ON COLUMN ai_model_metrics.operation_type IS 'Type of AI operation performed';
COMMENT ON COLUMN ai_model_metrics.input_tokens IS 'Number of tokens sent to the model';
COMMENT ON COLUMN ai_model_metrics.output_tokens IS 'Number of tokens received from the model';
COMMENT ON COLUMN ai_model_metrics.latency_ms IS 'Response time in milliseconds';
COMMENT ON COLUMN ai_model_metrics.success IS 'Whether the operation succeeded';
COMMENT ON COLUMN ai_model_metrics.fallback_used IS 'Whether this was a fallback call after primary model failed';
COMMENT ON COLUMN ai_model_metrics.error_reason IS 'Error description if operation failed';

-- Index for tenant-based queries (dashboard, billing)
CREATE INDEX idx_ai_metrics_tenant_id ON ai_model_metrics(tenant_id);

-- Index for test run analysis
CREATE INDEX idx_ai_metrics_test_run_id ON ai_model_metrics(test_run_id);

-- Index for time-based analytics
CREATE INDEX idx_ai_metrics_created_at ON ai_model_metrics(created_at);

-- Index for provider analysis
CREATE INDEX idx_ai_metrics_provider ON ai_model_metrics(model_provider, success);

-- Index for fallback rate analysis
CREATE INDEX idx_ai_metrics_fallback ON ai_model_metrics(tenant_id, fallback_used)
    WHERE fallback_used = true;
