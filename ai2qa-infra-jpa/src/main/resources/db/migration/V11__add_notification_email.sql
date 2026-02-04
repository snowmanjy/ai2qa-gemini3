-- Add notification_email column to tenant table
-- Stores the user's preferred email for test completion notifications
-- Can be different from their Clerk login email

ALTER TABLE tenant ADD COLUMN notification_email VARCHAR(255);

-- Comment for documentation
COMMENT ON COLUMN tenant.notification_email IS 'Email address for test completion notifications (optional, defaults to Clerk email)';
