-- Add persona column to test_run table
-- Defaults to 'STANDARD' for existing and new rows

ALTER TABLE test_run
ADD COLUMN persona VARCHAR(50) DEFAULT 'STANDARD' NOT NULL;

-- Add index for potential filtering by persona
CREATE INDEX idx_test_run_persona ON test_run(persona);

-- Add comment explaining the column
COMMENT ON COLUMN test_run.persona IS 'Test persona that shapes AI agent behavior: STANDARD, CHAOS, or HACKER';
