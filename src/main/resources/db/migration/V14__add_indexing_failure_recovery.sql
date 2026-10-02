-- Persist only failed repository chunk IDs so successful Chroma work is reused on retry.
ALTER TABLE documentation_jobs ADD COLUMN IF NOT EXISTS indexing_failures_json text;
