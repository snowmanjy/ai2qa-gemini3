-- V9: Remove subscription plan system
-- Ai2QA is now using a pure pay-per-credit model without subscription plans

-- Remove the current_plan column from tenant table
ALTER TABLE tenant DROP COLUMN IF EXISTS current_plan;

-- Drop the check constraint if exists (it may have been created in V2)
ALTER TABLE tenant DROP CONSTRAINT IF EXISTS chk_tenant_plan;

-- Note: credits_balance defaults to 3 (from V2) and is preserved for existing users
-- New tenants will get 3 free credits by default
