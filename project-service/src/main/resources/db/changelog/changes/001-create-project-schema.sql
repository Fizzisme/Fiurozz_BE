--liquibase formatted sql

--changeset philia:001-create-project-schema dbms:postgresql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE project_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(80) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(120) NOT NULL,
    icon VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_categories_key UNIQUE (key),
    CONSTRAINT uq_project_categories_slug UNIQUE (slug),
    CONSTRAINT ck_project_categories_key_non_blank CHECK (btrim(key) <> ''),
    CONSTRAINT ck_project_categories_slug_non_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_project_categories_title_non_blank CHECK (btrim(title) <> '')
);

CREATE TABLE project_sub_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL,
    key VARCHAR(80) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_categories_category
        FOREIGN KEY (category_id) REFERENCES project_categories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_sub_categories_category_key UNIQUE (category_id, key),
    CONSTRAINT uq_sub_categories_category_slug UNIQUE (category_id, slug),
    CONSTRAINT ck_sub_categories_key_non_blank CHECK (btrim(key) <> ''),
    CONSTRAINT ck_sub_categories_slug_non_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_sub_categories_title_non_blank CHECK (btrim(title) <> '')
);

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    owner_display_name VARCHAR(120) NOT NULL,
    owner_avatar_url VARCHAR(500),
    sub_category_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    short_description VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    thumbnail_url VARCHAR(500),
    demo_url VARCHAR(500),
    tech_stack JSONB NOT NULL DEFAULT '[]'::jsonb,
    features JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    source_visibility VARCHAR(20) NOT NULL DEFAULT 'HIDDEN',
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_projects_sub_category
        FOREIGN KEY (sub_category_id) REFERENCES project_sub_categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_projects_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_projects_visibility
        CHECK (visibility IN ('PUBLIC', 'UNLISTED', 'PRIVATE')),
    CONSTRAINT ck_projects_source_visibility
        CHECK (source_visibility IN ('PUBLIC', 'OWNER_ONLY', 'COLLABORATORS', 'HIDDEN')),
    CONSTRAINT ck_projects_tech_stack_array CHECK (jsonb_typeof(tech_stack) = 'array'),
    CONSTRAINT ck_projects_features_array CHECK (jsonb_typeof(features) = 'array'),
    CONSTRAINT ck_projects_counters_non_negative
        CHECK (view_count >= 0 AND like_count >= 0 AND comment_count >= 0),
    CONSTRAINT ck_projects_published_at
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT ck_projects_title_non_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_projects_slug_non_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_projects_short_description_non_blank CHECK (btrim(short_description) <> ''),
    CONSTRAINT ck_projects_description_non_blank CHECK (btrim(description) <> ''),
    CONSTRAINT ck_projects_row_version_non_negative CHECK (row_version >= 0)
);

CREATE TABLE project_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    version_name VARCHAR(80) NOT NULL,
    release_notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    snapshot_ready_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_versions_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_project_versions_project_name UNIQUE (project_id, version_name),
    CONSTRAINT uq_project_versions_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_project_versions_status
        CHECK (status IN ('DRAFT', 'PREPARING', 'READY', 'PUBLISHED', 'FAILED', 'ARCHIVED')),
    CONSTRAINT ck_project_versions_version_name_non_blank CHECK (btrim(version_name) <> ''),
    CONSTRAINT ck_project_versions_snapshot_ready_at
        CHECK (status NOT IN ('READY', 'PUBLISHED') OR snapshot_ready_at IS NOT NULL),
    CONSTRAINT ck_project_versions_published_at
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT ck_project_versions_current_published
        CHECK (NOT is_current OR status = 'PUBLISHED'),
    CONSTRAINT ck_project_versions_row_version_non_negative CHECK (row_version >= 0)
);

CREATE TABLE tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(120) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tags_slug UNIQUE (slug),
    CONSTRAINT uq_tags_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_tags_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_tags_slug_non_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_tags_display_name_non_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_tags_normalized_name_non_blank CHECK (btrim(normalized_name) <> '')
);

CREATE TABLE project_tags (
    project_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_project_tags PRIMARY KEY (project_id, tag_id),
    CONSTRAINT fk_project_tags_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_tags_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE TABLE project_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    media_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    alt_text VARCHAR(500),
    width INTEGER,
    height INTEGER,
    duration_seconds INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_media_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_media_type CHECK (media_type IN ('IMAGE', 'GIF', 'VIDEO')),
    CONSTRAINT ck_project_media_url_non_blank CHECK (btrim(media_url) <> ''),
    CONSTRAINT ck_project_media_dimensions
        CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0)),
    CONSTRAINT ck_project_media_duration
        CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

CREATE TABLE github_integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    installation_id BIGINT NOT NULL,
    account_login VARCHAR(255) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    repository_selection VARCHAR(30) NOT NULL,
    permissions JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_synced_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_github_integrations_installation UNIQUE (installation_id),
    CONSTRAINT ck_github_integrations_account_type
        CHECK (account_type IN ('USER', 'ORGANIZATION')),
    CONSTRAINT ck_github_integrations_repository_selection
        CHECK (repository_selection IN ('ALL', 'SELECTED')),
    CONSTRAINT ck_github_integrations_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_github_integrations_permissions_object
        CHECK (jsonb_typeof(permissions) = 'object'),
    CONSTRAINT ck_github_integrations_account_login_non_blank
        CHECK (btrim(account_login) <> ''),
    CONSTRAINT ck_github_integrations_lifecycle
        CHECK (
            (status <> 'SUSPENDED' OR suspended_at IS NOT NULL)
            AND (status <> 'REVOKED' OR revoked_at IS NOT NULL)
        )
);

CREATE TABLE project_repositories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    github_integration_id UUID NOT NULL,
    github_repository_id BIGINT NOT NULL,
    full_name VARCHAR(300) NOT NULL,
    default_branch VARCHAR(255) NOT NULL,
    is_private BOOLEAN NOT NULL,
    html_url VARCHAR(500) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    latest_commit_sha VARCHAR(64),
    access_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_access_checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_repositories_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_repositories_integration
        FOREIGN KEY (github_integration_id) REFERENCES github_integrations (id) ON DELETE RESTRICT,
    CONSTRAINT uq_project_repositories_project_github_repo
        UNIQUE (project_id, github_repository_id),
    CONSTRAINT uq_project_repositories_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_project_repositories_access_status
        CHECK (access_status IN ('ACTIVE', 'INACCESSIBLE', 'REMOVED')),
    CONSTRAINT ck_project_repositories_full_name_non_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT ck_project_repositories_default_branch_non_blank CHECK (btrim(default_branch) <> ''),
    CONSTRAINT ck_project_repositories_html_url_non_blank CHECK (btrim(html_url) <> ''),
    CONSTRAINT ck_project_repositories_latest_commit_sha
        CHECK (
            latest_commit_sha IS NULL
            OR (length(latest_commit_sha) IN (40, 64) AND latest_commit_sha ~ '^[0-9A-Fa-f]+$')
        )
);

CREATE TABLE repository_source_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_repository_id UUID NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    storage_provider VARCHAR(20) NOT NULL DEFAULT 'MINIO',
    bucket_name VARCHAR(120) NOT NULL,
    archive_object_key VARCHAR(1024),
    manifest_object_key VARCHAR(1024),
    metadata_object_key VARCHAR(1024),
    archive_format VARCHAR(30),
    archive_size_bytes BIGINT,
    uncompressed_size_bytes BIGINT,
    file_count INTEGER,
    content_checksum VARCHAR(100),
    manifest_schema_version INTEGER NOT NULL DEFAULT 1,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(80),
    error_message TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ,
    retention_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_source_snapshots_repository
        FOREIGN KEY (project_repository_id) REFERENCES project_repositories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_source_snapshots_repository_commit
        UNIQUE (project_repository_id, commit_sha),
    CONSTRAINT uq_source_snapshots_identity
        UNIQUE (id, project_repository_id, commit_sha),
    CONSTRAINT ck_source_snapshots_commit_sha
        CHECK (length(commit_sha) IN (40, 64) AND commit_sha ~ '^[0-9A-Fa-f]+$'),
    CONSTRAINT ck_source_snapshots_status
        CHECK (status IN ('REQUESTED', 'DOWNLOADING', 'PROCESSING', 'READY', 'FAILED', 'DELETING', 'DELETED')),
    CONSTRAINT ck_source_snapshots_storage_provider
        CHECK (storage_provider IN ('MINIO', 'S3')),
    CONSTRAINT ck_source_snapshots_archive_format
        CHECK (archive_format IS NULL OR archive_format IN ('TAR_ZSTD', 'ZIP')),
    CONSTRAINT ck_source_snapshots_metrics_non_negative
        CHECK (
            (archive_size_bytes IS NULL OR archive_size_bytes >= 0)
            AND (uncompressed_size_bytes IS NULL OR uncompressed_size_bytes >= 0)
            AND (file_count IS NULL OR file_count >= 0)
            AND manifest_schema_version > 0
            AND attempt_count >= 0
            AND row_version >= 0
        ),
    CONSTRAINT ck_source_snapshots_bucket_non_blank CHECK (btrim(bucket_name) <> ''),
    CONSTRAINT ck_source_snapshots_completed_state
        CHECK (status NOT IN ('READY', 'FAILED') OR completed_at IS NOT NULL),
    CONSTRAINT ck_source_snapshots_ready_artifacts
        CHECK (
            status <> 'READY'
            OR (
                archive_object_key IS NOT NULL
                AND manifest_object_key IS NOT NULL
                AND metadata_object_key IS NOT NULL
                AND archive_format IS NOT NULL
                AND archive_size_bytes IS NOT NULL
                AND uncompressed_size_bytes IS NOT NULL
                AND file_count IS NOT NULL
                AND content_checksum IS NOT NULL
                AND completed_at IS NOT NULL
            )
        )
);

CREATE TABLE project_version_repository_refs (
    project_version_id UUID NOT NULL,
    project_repository_id UUID NOT NULL,
    project_id UUID NOT NULL,
    ref_type VARCHAR(20) NOT NULL,
    ref_name VARCHAR(255),
    commit_sha VARCHAR(64) NOT NULL,
    source_snapshot_id UUID,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_project_version_repository_refs
        PRIMARY KEY (project_version_id, project_repository_id),
    CONSTRAINT fk_version_refs_version_project
        FOREIGN KEY (project_version_id, project_id)
        REFERENCES project_versions (id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_version_refs_repository_project
        FOREIGN KEY (project_repository_id, project_id)
        REFERENCES project_repositories (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT fk_version_refs_snapshot_identity
        FOREIGN KEY (source_snapshot_id, project_repository_id, commit_sha)
        REFERENCES repository_source_snapshots (id, project_repository_id, commit_sha)
        ON DELETE RESTRICT,
    CONSTRAINT ck_version_refs_ref_type CHECK (ref_type IN ('BRANCH', 'TAG', 'COMMIT')),
    CONSTRAINT ck_version_refs_ref_name
        CHECK (ref_type = 'COMMIT' OR (ref_name IS NOT NULL AND btrim(ref_name) <> '')),
    CONSTRAINT ck_version_refs_commit_sha
        CHECK (length(commit_sha) IN (40, 64) AND commit_sha ~ '^[0-9A-Fa-f]+$')
);

CREATE TABLE github_webhook_deliveries (
    delivery_id VARCHAR(100) PRIMARY KEY,
    event_name VARCHAR(100) NOT NULL,
    event_action VARCHAR(100),
    github_integration_id UUID,
    github_repository_id BIGINT,
    signature_valid BOOLEAN NOT NULL,
    payload JSONB NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT fk_webhook_deliveries_integration
        FOREIGN KEY (github_integration_id) REFERENCES github_integrations (id) ON DELETE SET NULL,
    CONSTRAINT ck_webhook_deliveries_status
        CHECK (processing_status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED', 'IGNORED')),
    CONSTRAINT ck_webhook_deliveries_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_webhook_deliveries_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_webhook_deliveries_processed_at
        CHECK (processing_status NOT IN ('PROCESSED', 'IGNORED') OR processed_at IS NOT NULL),
    CONSTRAINT ck_webhook_deliveries_id_non_blank CHECK (btrim(delivery_id) <> ''),
    CONSTRAINT ck_webhook_deliveries_event_non_blank CHECK (btrim(event_name) <> '')
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_events_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_outbox_events_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_events_published_at
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT ck_outbox_events_aggregate_type_non_blank CHECK (btrim(aggregate_type) <> ''),
    CONSTRAINT ck_outbox_events_event_type_non_blank CHECK (btrim(event_type) <> '')
);

CREATE TABLE project_likes (
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_project_likes PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_likes_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE project_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    author_id UUID NOT NULL,
    author_display_name VARCHAR(120) NOT NULL,
    author_avatar_url VARCHAR(500),
    parent_comment_id UUID,
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'VISIBLE',
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_comments_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_project_comments_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_comments_parent_same_project
        FOREIGN KEY (parent_comment_id, project_id)
        REFERENCES project_comments (id, project_id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_comments_status
        CHECK (status IN ('VISIBLE', 'HIDDEN', 'DELETED')),
    CONSTRAINT ck_project_comments_content_non_blank CHECK (btrim(content) <> ''),
    CONSTRAINT ck_project_comments_author_name_non_blank CHECK (btrim(author_display_name) <> ''),
    CONSTRAINT ck_project_comments_deleted_at
        CHECK (status <> 'DELETED' OR deleted_at IS NOT NULL)
);

--rollback DROP TABLE project_comments;
--rollback DROP TABLE project_likes;
--rollback DROP TABLE outbox_events;
--rollback DROP TABLE github_webhook_deliveries;
--rollback DROP TABLE project_version_repository_refs;
--rollback DROP TABLE repository_source_snapshots;
--rollback DROP TABLE project_repositories;
--rollback DROP TABLE github_integrations;
--rollback DROP TABLE project_media;
--rollback DROP TABLE project_tags;
--rollback DROP TABLE tags;
--rollback DROP TABLE project_versions;
--rollback DROP TABLE projects;
--rollback DROP TABLE project_sub_categories;
--rollback DROP TABLE project_categories;
