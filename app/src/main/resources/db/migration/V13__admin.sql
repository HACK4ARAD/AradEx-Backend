-- V13 (T14): administrare. Conturi dezactivate de Admin și firme respinse de Primărie/Admin. Vezi docs/DATA-MODEL.md.
ALTER TABLE users      ADD COLUMN IF NOT EXISTS disabled_at   TIMESTAMPTZ;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS rejected_at   TIMESTAMPTZ;
ALTER TABLE businesses ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(300);

-- lista „De verificat” (firme neverificate, nerespinse) și filtrul pe roluri din /admin/users
CREATE INDEX IF NOT EXISTS idx_businesses_verified ON businesses(verified, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
