-- V1 no longer accepts or stores user-uploaded reference/college-template files.
-- Remove legacy columns that were used by the retired draft/reference flow.
ALTER TABLE documentation_jobs DROP COLUMN IF EXISTS reference_file_path;
ALTER TABLE documentation_jobs DROP COLUMN IF EXISTS reference_filename;
ALTER TABLE documentation_jobs DROP COLUMN IF EXISTS reference_content_type;
