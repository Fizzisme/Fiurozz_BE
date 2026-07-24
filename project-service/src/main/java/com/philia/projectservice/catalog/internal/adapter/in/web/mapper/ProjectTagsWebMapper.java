package com.philia.projectservice.catalog.internal.adapter.in.web.mapper;

import com.philia.projectservice.catalog.api.ReplaceProjectTagsCommand;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsResult;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.ReplaceProjectTagsRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectTagsResponse;
import org.mapstruct.Mapper;

import java.util.UUID;

/** Converts the tags endpoint's HTTP DTOs to and from application-layer records. */
@Mapper(componentModel = "spring")
public interface ProjectTagsWebMapper {

    default ReplaceProjectTagsCommand toCommand(
            UUID projectId,
            long expectedVersion,
            ReplaceProjectTagsRequest request
    ) {
        return new ReplaceProjectTagsCommand(projectId, expectedVersion, request.tagIds());
    }

    default ProjectTagsResponse toResponse(ReplaceProjectTagsResult result) {
        return new ProjectTagsResponse(
                result.projectId(),
                result.tags().stream()
                        .map(tag -> new ProjectTagsResponse.TagResponse(tag.id(), tag.slug(), tag.displayName()))
                        .toList(),
                result.version()
        );
    }
}
