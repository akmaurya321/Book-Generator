CREATE TABLE template_catalog (
    id uuid PRIMARY KEY,
    template_id varchar(180) NOT NULL UNIQUE,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

CREATE TABLE template_versions (
    id uuid PRIMARY KEY,
    template_catalog_id uuid NOT NULL REFERENCES template_catalog(id) ON DELETE CASCADE,
    version integer NOT NULL CHECK (version > 0),
    country varchar(120),
    state varchar(120),
    region varchar(120),
    city varchar(120),
    college varchar(255),
    university varchar(255),
    aliases_json text NOT NULL,
    department varchar(180),
    degree varchar(120),
    project_type varchar(40),
    template_storage_key varchar(1024),
    preview_storage_key varchar(1024),
    front_page_config_json text NOT NULL,
    format_schema_json text NOT NULL,
    analysis_metadata_json text NOT NULL DEFAULT '{}',
    preview_text text NOT NULL DEFAULT '',
    source_type varchar(24) NOT NULL,
    source_url varchar(2048),
    license varchar(500),
    ownership_declaration text,
    ownership_confirmed_at timestamp without time zone,
    status varchar(24) NOT NULL,
    verification_status varchar(24) NOT NULL,
    available boolean NOT NULL DEFAULT false,
    created_by uuid REFERENCES app_users(id) ON DELETE SET NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    verified_by uuid REFERENCES app_users(id) ON DELETE SET NULL,
    verified_at timestamp without time zone,
    security_scan_status varchar(24) NOT NULL,
    validation_status varchar(24) NOT NULL,
    published_at timestamp without time zone,
    suspended_at timestamp without time zone,
    CONSTRAINT uq_template_catalog_version UNIQUE (template_catalog_id, version),
    CONSTRAINT ck_template_version_project_type CHECK (
        project_type IS NULL OR project_type IN (
            'MINOR_PROJECT',
            'MAJOR_FINAL_YEAR_PROJECT',
            'CAPSTONE',
            'BACHELOR_PROJECT',
            'BACHELOR_THESIS'
        )
    ),
    CONSTRAINT ck_template_version_status CHECK (
        status IN ('DRAFT', 'PROCESSING', 'UNDER_REVIEW', 'APPROVED', 'PUBLISHED', 'REJECTED', 'SUSPENDED', 'ARCHIVED')
    )
);

CREATE INDEX idx_template_version_status_available
    ON template_versions(status, available, published_at DESC);
CREATE INDEX idx_template_version_country_state_city
    ON template_versions(country, state, city);
CREATE INDEX idx_template_version_college_department
    ON template_versions(college, department);
CREATE INDEX idx_template_version_degree_project_type
    ON template_versions(degree, project_type);

CREATE TABLE template_audit_events (
    id uuid PRIMARY KEY,
    template_version_id uuid NOT NULL REFERENCES template_versions(id) ON DELETE CASCADE,
    actor_id uuid REFERENCES app_users(id) ON DELETE SET NULL,
    action varchar(40) NOT NULL,
    old_status varchar(24),
    new_status varchar(24),
    reason varchar(2000),
    created_at timestamp without time zone NOT NULL
);
CREATE INDEX idx_template_audit_version_created
    ON template_audit_events(template_version_id, created_at DESC);

ALTER TABLE documentation_jobs
    ADD COLUMN template_id varchar(180),
    ADD COLUMN template_version integer,
    ADD COLUMN template_format_snapshot_json text,
    ADD COLUMN template_front_page_snapshot_json text;

CREATE INDEX idx_documentation_jobs_template
    ON documentation_jobs(template_id, template_version);

INSERT INTO template_catalog (id, template_id, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'universal-template', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO template_versions (
    id, template_catalog_id, version, aliases_json, front_page_config_json, format_schema_json,
    source_type, license, status, verification_status, available, created_at, updated_at,
    security_scan_status, validation_status, published_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    1,
    '[]',
    '{}',
    '{"pageSize":"A4","orientation":"PORTRAIT","marginTopTwips":1440,"marginBottomTwips":1440,"marginLeftTwips":1440,"marginRightTwips":1440,"defaultFont":"Times New Roman","defaultFontSize":12,"lineSpacing":1.5,"titleFontSize":20,"heading1FontSize":16,"heading2FontSize":14,"heading3FontSize":12,"pageNumbers":true,"tableOfContents":true,"figures":true,"tables":true}',
    'SYSTEM',
    'DocGen AI Universal Template',
    'PUBLISHED',
    'VERIFIED',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'NOT_REQUIRED',
    'VALID',
    CURRENT_TIMESTAMP
);
