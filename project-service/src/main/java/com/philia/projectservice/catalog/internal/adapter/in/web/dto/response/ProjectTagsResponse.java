package com.philia.projectservice.catalog.internal.adapter.in.web.dto.response;

import java.util.List;
import java.util.UUID;

/** HTTP response data for a successful tag replacement. */
public record ProjectTagsResponse(UUID projectId, List<TagResponse> tags, long version) {

    public ProjectTagsResponse {
        tags = List.copyOf(tags);
    }

    /** Tag representation exposed by this endpoint. */
    public record TagResponse(UUID id, String slug, String displayName) {
    }
}
