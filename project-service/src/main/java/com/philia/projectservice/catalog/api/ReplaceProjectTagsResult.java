package com.philia.projectservice.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * Result returned after a project's tags are successfully replaced.
 * The version is the new value callers must use in the next If-Match header.
 */
public record ReplaceProjectTagsResult(UUID projectId, List<Tag> tags, long version) {

    public ReplaceProjectTagsResult {
        tags = List.copyOf(tags);
    }

    /** Public tag information returned to the caller. */
    public record Tag(UUID id, String slug, String displayName) {
    }
}
