-- V46: SoD policy engine enhancements
--   1. Add exemption expiry to violations
--   2. Add unique partial index to prevent duplicate open violations
--   3. Add unique constraint to prevent duplicate policies for same group pair

-- Exemption expiry: allows time-limited exemptions that auto-reopen
ALTER TABLE sod_violations ADD COLUMN exemption_expires_at TIMESTAMPTZ;

-- Prevent duplicate open violations for the same user+policy
CREATE UNIQUE INDEX idx_sod_violations_open_unique
    ON sod_violations (policy_id, user_dn)
    WHERE status = 'OPEN';

-- Prevent duplicate policies for the same group pair on a directory (order-independent)
CREATE UNIQUE INDEX idx_sod_policies_group_pair_unique
    ON sod_policies (directory_id, LEAST(group_a_dn, group_b_dn), GREATEST(group_a_dn, group_b_dn))
    WHERE enabled = true;
