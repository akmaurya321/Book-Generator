-- Safety baseline for fresh installations.
-- The application historically shipped the initial schema outside this repository;
-- this migration makes a clean PostgreSQL database bootable without changing the
-- later V6+ migration history. Every object is guarded so an existing database
-- can safely ignore this lower-version migration after its earlier history exists.

CREATE TABLE IF NOT EXISTS app_users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL,
    password_hash varchar(255) NOT NULL DEFAULT '',
    display_name varchar(255) NOT NULL,
    role varchar(64) NOT NULL DEFAULT 'ROLE_USER',
    name varchar(255) NOT NULL,
    avatar_url varchar(2048),
    provider varchar(64) NOT NULL,
    provider_user_id varchar(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_email ON app_users(email);
CREATE INDEX IF NOT EXISTS idx_app_user_provider ON app_users(provider);

CREATE TABLE IF NOT EXISTS app_user_roles (
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role varchar(255)
);
CREATE INDEX IF NOT EXISTS idx_app_user_roles_user_id ON app_user_roles(user_id);

CREATE TABLE IF NOT EXISTS documentation_jobs (
    job_id varchar(255) PRIMARY KEY,
    github_url varchar(2048),
    project_name varchar(255),
    owner_id uuid,
    attempt_count integer NOT NULL DEFAULT 0,
    status varchar(64),
    type varchar(128),
    document_path varchar(2048),
    pdf_path varchar(2048),
    project_facts_json text,
    plan_json text,
    repository_snapshot_json text,
    reference_file_path varchar(2048),
    reference_filename varchar(255),
    reference_content_type varchar(255),
    generation_checkpoint_json text,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    expires_at timestamp without time zone
);
CREATE INDEX IF NOT EXISTS idx_documentation_jobs_owner_id ON documentation_jobs(owner_id);

CREATE TABLE IF NOT EXISTS usage_events (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id varchar(36) NOT NULL UNIQUE,
    operation varchar(64) NOT NULL DEFAULT 'DOCUMENTATION_GENERATION',
    units integer NOT NULL DEFAULT 1,
    status varchar(32) NOT NULL DEFAULT 'QUEUED',
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_usage_events_owner_created ON usage_events(owner_id, created_at DESC);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone NOT NULL,
    used_at timestamp without time zone
);
CREATE INDEX IF NOT EXISTS idx_password_reset_token_user ON password_reset_tokens(user_id);
