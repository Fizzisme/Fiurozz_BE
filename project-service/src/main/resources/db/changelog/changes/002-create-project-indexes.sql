--liquibase formatted sql

--changeset philia:002-create-project-indexes dbms:postgresql
CREATE INDEX idx_project_categories_active_sort
    ON project_categories (sort_order, title)
    WHERE is_active = TRUE AND deleted_at IS NULL;

CREATE INDEX idx_sub_categories_category_active_sort
    ON project_sub_categories (category_id, sort_order, title)
    WHERE is_active = TRUE AND deleted_at IS NULL;

CREATE UNIQUE INDEX uq_projects_owner_slug_active
    ON projects (owner_id, slug)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_projects_discovery
    ON projects (sub_category_id, status, visibility, published_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_projects_owner_created
    ON projects (owner_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_project_versions_current
    ON project_versions (project_id)
    WHERE is_current = TRUE;

CREATE INDEX idx_project_versions_project_created
    ON project_versions (project_id, created_at DESC);

CREATE INDEX idx_tags_status_display_name
    ON tags (status, display_name);

CREATE INDEX idx_project_tags_tag
    ON project_tags (tag_id, project_id);

CREATE INDEX idx_project_media_project_sort
    ON project_media (project_id, sort_order, created_at);

CREATE INDEX idx_github_integrations_user_status
    ON github_integrations (user_id, status);

CREATE UNIQUE INDEX uq_project_repositories_primary
    ON project_repositories (project_id)
    WHERE is_primary = TRUE;

CREATE INDEX idx_project_repositories_integration
    ON project_repositories (github_integration_id, access_status);

CREATE INDEX idx_source_snapshots_worker_claim
    ON repository_source_snapshots (status, requested_at)
    WHERE status IN ('REQUESTED', 'FAILED');

CREATE INDEX idx_source_snapshots_gc
    ON repository_source_snapshots (retention_until, last_accessed_at)
    WHERE status IN ('READY', 'FAILED') AND retention_until IS NOT NULL;

CREATE INDEX idx_version_refs_snapshot
    ON project_version_repository_refs (source_snapshot_id)
    WHERE source_snapshot_id IS NOT NULL;

CREATE INDEX idx_webhook_deliveries_retry
    ON github_webhook_deliveries (processing_status, received_at)
    WHERE processing_status IN ('RECEIVED', 'FAILED');

CREATE INDEX idx_outbox_events_publish
    ON outbox_events (status, available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_project_likes_user_created
    ON project_likes (user_id, created_at DESC);

CREATE INDEX idx_project_comments_project_parent_created
    ON project_comments (project_id, parent_comment_id, created_at DESC)
    WHERE status <> 'DELETED';

CREATE INDEX idx_project_comments_author_created
    ON project_comments (author_id, created_at DESC);

--rollback DROP INDEX idx_project_comments_author_created;
--rollback DROP INDEX idx_project_comments_project_parent_created;
--rollback DROP INDEX idx_project_likes_user_created;
--rollback DROP INDEX idx_outbox_events_publish;
--rollback DROP INDEX idx_webhook_deliveries_retry;
--rollback DROP INDEX idx_version_refs_snapshot;
--rollback DROP INDEX idx_source_snapshots_gc;
--rollback DROP INDEX idx_source_snapshots_worker_claim;
--rollback DROP INDEX idx_project_repositories_integration;
--rollback DROP INDEX uq_project_repositories_primary;
--rollback DROP INDEX idx_github_integrations_user_status;
--rollback DROP INDEX idx_project_media_project_sort;
--rollback DROP INDEX idx_project_tags_tag;
--rollback DROP INDEX idx_tags_status_display_name;
--rollback DROP INDEX idx_project_versions_project_created;
--rollback DROP INDEX uq_project_versions_current;
--rollback DROP INDEX idx_projects_owner_created;
--rollback DROP INDEX idx_projects_discovery;
--rollback DROP INDEX uq_projects_owner_slug_active;
--rollback DROP INDEX idx_sub_categories_category_active_sort;
--rollback DROP INDEX idx_project_categories_active_sort;
