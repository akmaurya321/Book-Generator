CREATE TABLE IF NOT EXISTS usage_events (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id varchar(36) NOT NULL UNIQUE,
    operation varchar(64) NOT NULL,
    units integer NOT NULL DEFAULT 1,
    status varchar(32) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_usage_events_owner_created
    ON usage_events (owner_id, created_at DESC);
