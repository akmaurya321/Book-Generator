CREATE TABLE marketplace_listings (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_users(id),
    slug varchar(180) NOT NULL UNIQUE,
    listing_type varchar(40) NOT NULL,
    origin_type varchar(20) NOT NULL,
    title varchar(180) NOT NULL,
    description text NOT NULL,
    category varchar(80) NOT NULL,
    technologies text,
    project_name varchar(255),
    source_job_id varchar(255) REFERENCES documentation_jobs(job_id) ON DELETE SET NULL,
    project_file_path varchar(2048),
    document_file_path varchar(2048),
    preview_text text,
    status varchar(24) NOT NULL,
    ownership_confirmed boolean NOT NULL DEFAULT false,
    ownership_confirmed_at timestamp without time zone,
    rejection_reason varchar(1000),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    submitted_at timestamp without time zone,
    published_at timestamp without time zone
);

CREATE INDEX idx_marketplace_listing_public
    ON marketplace_listings(status, category, published_at DESC);
CREATE INDEX idx_marketplace_listing_owner
    ON marketplace_listings(owner_id, updated_at DESC);

CREATE TABLE marketplace_audit_events (
    id uuid PRIMARY KEY,
    listing_id uuid NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
    actor_id uuid REFERENCES app_users(id) ON DELETE SET NULL,
    action varchar(40) NOT NULL,
    details varchar(2000),
    created_at timestamp without time zone NOT NULL
);
CREATE INDEX idx_marketplace_audit_listing
    ON marketplace_audit_events(listing_id, created_at DESC);
