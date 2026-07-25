package com.philia.productservice.catalog.internal.adapter.in.web;

import com.philia.productservice.catalog.api.CreateProjectUseCase;
import com.philia.productservice.catalog.internal.adapter.in.web.documentation.CreateProjectApiDocumentation;
import com.philia.productservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import com.philia.productservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import com.philia.productservice.catalog.internal.adapter.in.web.mapper.CreateProjectWebMapper;
import com.philia.productservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import com.philia.productservice.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/projects")
public final class ProjectCommandController implements CreateProjectApiDocumentation {

    private final CreateProjectUseCase createProjectUseCase;
    private final CreateProjectWebMapper createProjectWebMapper;
    private final ProjectDetailWebMapper projectDetailWebMapper;

    public ProjectCommandController(
            CreateProjectUseCase createProjectUseCase,
            CreateProjectWebMapper createProjectWebMapper,
            ProjectDetailWebMapper projectDetailWebMapper
    ) {
        this.createProjectUseCase = createProjectUseCase;
        this.createProjectWebMapper = createProjectWebMapper;
        this.projectDetailWebMapper = projectDetailWebMapper;
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        var command = createProjectWebMapper.toCommand(request);
        var result = createProjectUseCase.create(command);
        var response = projectDetailWebMapper.toResponse(result);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{projectId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location)
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(ApiResponse.success(
                        "PROJECT_CREATED",
                        "Project created successfully.",
                        response
                ));
    }
}
