package com.philia.projectservice.catalog.internal.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence operations required by the replace-tags use case.
 * The application layer depends on this port rather than directly on JPA.
 */
public interface ProjectTagCommandGateway {

    /** Loads only the ownership and version data needed for authorization and ETag validation. */
    Optional<ProjectState> findActiveState(UUID projectId);

    /** Atomically increments the version only when the supplied ETag is still current. */
    boolean advanceVersion(UUID projectId, UUID ownerId, long expectedVersion, Instant updatedAt);

    /** Replaces all project-tag rows with the supplied tag IDs. */
    void replaceTags(UUID projectId, Set<UUID> tagIds);

    /** Minimal project data needed before a tag replacement. */
    record ProjectState(UUID ownerId, long version) {
    }
}
