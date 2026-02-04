-- Add summary_status column to track AI summary generation lifecycle
-- This allows the frontend to show loading states while summary is being generated

ALTER TABLE test_run
ADD COLUMN summary_status VARCHAR(20) DEFAULT 'PENDING';

-- Set existing rows: if summary exists, mark as COMPLETED; otherwise PENDING
-- Note: summary_json is JSONB, so we can't compare with empty string directly
UPDATE test_run
SET summary_status = CASE
    WHEN summary_json IS NOT NULL AND summary_json::text != 'null' THEN 'COMPLETED'
    ELSE 'PENDING'
END;

-- Add comment for documentation
COMMENT ON COLUMN test_run.summary_status IS 'Status of AI summary generation: PENDING, GENERATING, COMPLETED, FAILED';
