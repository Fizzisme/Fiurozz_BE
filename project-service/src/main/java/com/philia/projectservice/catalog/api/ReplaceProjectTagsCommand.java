package com.philia.projectservice.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * Input for replacing every tag assigned to one project.
 *
 * @param projectId identifier of the project to modify
 * @param expectedVersion version supplied in the HTTP If-Match header
 * @param tagIds complete replacement collection; an empty list removes all tags
 */
public record ReplaceProjectTagsCommand(
        UUID projectId,
        long expectedVersion,
        List<UUID> tagIds
) {
}
