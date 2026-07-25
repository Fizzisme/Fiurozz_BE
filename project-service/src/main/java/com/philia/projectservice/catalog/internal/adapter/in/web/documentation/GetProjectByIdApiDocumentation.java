package com.philia.projectservice.catalog.internal.adapter.in.web.documentation;

import com.philia.projectservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.projectservice.shared.openapi.ApiErrorResponseDocumentation;
import com.philia.projectservice.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * OpenAPI-only contract for Get Project by ID. Runtime request handling remains in
 * {@code ProjectQueryController}.
 */
@Tag(
        name = "Project Catalog",
        description = "Create and retrieve projects from the catalog."
)
public interface GetProjectByIdApiDocumentation {

    @Operation(
            operationId = "getProjectById",
            summary = "Get a project by ID",
            description = """
                    Returns a complete project representation.

                    Authentication is optional. The owner can read any of their active projects,
                    including draft, private, and archived projects. Anonymous users and other
                    owners can read only published projects whose visibility is PUBLIC or
                    UNLISTED. Missing, deleted, and inaccessible projects all return 404 so that
                    private project existence is not disclosed.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Project retrieved successfully.",
                    useReturnTypeSchema = true,
                    headers = @Header(
                            name = "ETag",
                            description = "Current optimistic-lock version of the project.",
                            schema = @Schema(type = "string", example = "\"2\"")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "The project ID is not a valid UUID.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "The project is missing, deleted, or inaccessible to the caller.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponseDocumentation.class)
                    )
            )
    })
    ResponseEntity<ApiResponse<ProjectDetailResponse>> getProjectById(
            @Parameter(
                    description = "Project UUID.",
                    required = true,
                    example = "ff82810c-bb24-46cf-b25f-48cb96532cda"
            )
            UUID projectId
    );
}
