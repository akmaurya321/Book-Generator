ALTER TABLE documentation_jobs
    ADD COLUMN IF NOT EXISTS editor_history_json text,
    ADD COLUMN IF NOT EXISTS editor_history_position integer NOT NULL DEFAULT -1;
