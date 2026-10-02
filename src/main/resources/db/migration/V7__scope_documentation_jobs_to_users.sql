ALTER TABLE documentation_jobs
    ADD COLUMN IF NOT EXISTS owner_id uuid;

CREATE INDEX IF NOT EXISTS idx_documentation_jobs_owner_id
    ON documentation_jobs (owner_id);