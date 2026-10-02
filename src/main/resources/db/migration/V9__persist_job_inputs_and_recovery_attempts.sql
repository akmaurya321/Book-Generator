ALTER TABLE documentation_jobs
    ADD COLUMN IF NOT EXISTS reference_file_path varchar(2048),
    ADD COLUMN IF NOT EXISTS reference_filename varchar(255),
    ADD COLUMN IF NOT EXISTS reference_content_type varchar(255),
    ADD COLUMN IF NOT EXISTS attempt_count integer NOT NULL DEFAULT 0;
