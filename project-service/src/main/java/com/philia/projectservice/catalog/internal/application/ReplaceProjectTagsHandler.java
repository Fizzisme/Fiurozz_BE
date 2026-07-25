package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ReplaceProjectTagsCommand;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsResult;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsUseCase;
import com.philia.projectservice.catalog.internal.application.exception.ProjectForbiddenException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectStaleVersionException;
import com.philia.projectservice.catalog.internal.application.exception.TagsUnavailableException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagCommandGateway;
import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates tag replacement, ownership checks, active-tag validation, and
 * optimistic concurrency for an existing project.
 */
@Service
public class ReplaceProjectTagsHandler implements ReplaceProjectTagsUseCase {

    private static final int MAX_TAGS = 10;

    private final CurrentActor currentActor;
    private final CatalogReferenceQuery catalogReferenceQuery;
    private final ProjectTagCommandGateway projectTagCommandGateway;
    private final Clock clock;

    public ReplaceProjectTagsHandler(
            CurrentActor currentActor,
            CatalogReferenceQuery catalogReferenceQuery,
            ProjectTagCommandGateway projectTagCommandGateway,
            Clock clock
    ) {
        this.currentActor = currentActor;
        this.catalogReferenceQuery = catalogReferenceQuery;
        this.projectTagCommandGateway = projectTagCommandGateway;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReplaceProjectTagsResult replaceProjectTags(ReplaceProjectTagsCommand command) {
        // Validate command data before reading from the database.
        if (command == null || command.projectId() == null) {
            throw new InvalidProjectException("Project ID is required.");
        }
        if (command.expectedVersion() < 0) {
            throw new InvalidProjectException("If-Match must contain a non-negative project version.");
        }

        // Only the project owner may change its tags.
        var actor = currentActor.getRequiredActor();
        var project = projectTagCommandGateway.findActiveState(command.projectId())
                .orElseThrow(ProjectNotFoundException::new);
        if (!actor.id().equals(project.ownerId())) {
            throw new ProjectForbiddenException();
        }
        if (project.version() != command.expectedVersion()) {
            throw new ProjectStaleVersionException();
        }

        // The supplied collection is the complete desired tag collection.
        var tagIds = uniqueTagIds(command.tagIds());
        var tags = loadAllActiveTags(tagIds);

        // This conditional update prevents two callers from replacing tags using
        // the same old ETag. It also advances row_version for the response ETag.
        if (!projectTagCommandGateway.advanceVersion(
                command.projectId(), actor.id(), command.expectedVersion(), clock.instant())) {
            throw new ProjectStaleVersionException();
        }
        projectTagCommandGateway.replaceTags(command.projectId(), tagIds);

        return new ReplaceProjectTagsResult(
                command.projectId(),
                tags.stream()
                        .map(tag -> new ReplaceProjectTagsResult.Tag(tag.id(), tag.slug(), tag.displayName()))
                        .toList(),
                command.expectedVersion() + 1
        );
    }

    private List<CatalogReferenceQuery.TagReference> loadAllActiveTags(Set<UUID> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        var tags = catalogReferenceQuery.findActiveTags(tagIds);
        var foundIds = new LinkedHashSet<UUID>();
        tags.forEach(tag -> foundIds.add(tag.id()));
        // Reject the request as a whole when even one requested tag is inactive or missing.
        var unavailable = new LinkedHashSet<>(tagIds);
        unavailable.removeAll(foundIds);
        if (!unavailable.isEmpty()) {
            throw new TagsUnavailableException(unavailable);
        }
        return tags;
    }

    private static Set<UUID> uniqueTagIds(List<UUID> values) {
        if (values == null) {
            throw new InvalidProjectException("tagIds is required.");
        }
        var tagIds = new LinkedHashSet<UUID>();
        for (var value : values) {
            if (value == null) {
                throw new InvalidProjectException("tagIds must not contain null values.");
            }
            tagIds.add(value);
        }
        if (tagIds.size() > MAX_TAGS) {
            throw new InvalidProjectException("A project may have at most 10 tags.");
        }
        return tagIds;
    }
}
