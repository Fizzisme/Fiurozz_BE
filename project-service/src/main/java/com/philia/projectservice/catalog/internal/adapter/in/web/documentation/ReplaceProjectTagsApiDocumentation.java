package com.philia.projectservice.catalog.internal.adapter.in.web.documentation;

import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.ReplaceProjectTagsRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectTagsResponse;
import com.philia.projectservice.shared.openapi.ApiErrorResponseDocumentation;
import com.philia.projectservice.shared.openapi.OpenApiConfiguration;
import com.philia.projectservice.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public interface ReplaceProjectTagsApiDocumentation {

    @Operation(
            operationId = "replaceProjectTags",
            summary = "Replace a project's tags",
            description = """
                    Replaces every tag assigned to a project owned by the authenticated actor.
                    The current ETag must be supplied through If-Match. An empty tagIds array
                    removes all tags.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Project tags replaced successfully.",
                    useReturnTypeSchema = true,
                    headers = @Header(
                            name = "ETag",
                            description = "New optimistic-lock version of the project.",
                            schema = @Schema(type = "string", example = "\"4\"")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "The request body or If-Match header is invalid.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "A valid authenticated actor is required.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "The authenticated actor does not own the project.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "The project does not exist or is deleted.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "One or more requested tags are unavailable.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "412",
                    description = "The supplied ETag is stale.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class))
            )
    })
    ResponseEntity<ApiResponse<ProjectTagsResponse>> replaceProjectTags(
            UUID projectId,
            String ifMatch,
            ReplaceProjectTagsRequest request
    );
}
