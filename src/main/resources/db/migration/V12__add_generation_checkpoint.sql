ALTER TABLE documentation_jobs
    ADD COLUMN IF NOT EXISTS generation_checkpoint_json text;
