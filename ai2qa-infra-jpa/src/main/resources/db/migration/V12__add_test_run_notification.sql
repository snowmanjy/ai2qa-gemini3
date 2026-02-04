-- Add notification settings to test_run table
-- Allows users to opt-in to email notifications when a test completes

ALTER TABLE test_run ADD COLUMN notify_on_complete BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE test_run ADD COLUMN notification_email VARCHAR(255);

-- Comments for documentation
COMMENT ON COLUMN test_run.notify_on_complete IS 'Whether to send email notification when test completes';
COMMENT ON COLUMN test_run.notification_email IS 'Override email for this test (uses tenant notification email if null)';
