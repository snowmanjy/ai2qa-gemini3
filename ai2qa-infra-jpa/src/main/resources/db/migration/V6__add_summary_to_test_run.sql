-- Add summary_json column to test_run table
-- This stores the AI-generated RunSummary for completed test runs

ALTER TABLE test_run
ADD COLUMN IF NOT EXISTS summary_json JSONB;

-- Create index for querying by summary status
CREATE INDEX IF NOT EXISTS idx_test_run_summary_status
ON test_run ((summary_json->>'status'))
WHERE summary_json IS NOT NULL;

COMMENT ON COLUMN test_run.summary_json IS 'AI-generated summary with status, goalOverview, outcomeShort, failureAnalysis, actionableFix, keyAchievements, and healthCheck';
