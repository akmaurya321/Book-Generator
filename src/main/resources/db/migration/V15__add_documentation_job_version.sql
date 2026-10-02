ALTER TABLE documentation_jobs ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
