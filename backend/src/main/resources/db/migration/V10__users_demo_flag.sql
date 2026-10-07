-- Marks a temporary visitor account created by the demo login. A scheduled cleanup deletes only rows
-- with this flag, so the shared template demo account and regular users are never swept.
ALTER TABLE users ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index: the cleanup scans demo accounts by age and most rows are not demo accounts.
CREATE INDEX idx_users_demo_created_at ON users(created_at) WHERE demo;
