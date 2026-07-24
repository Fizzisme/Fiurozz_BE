package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.CreateProjectUseCase;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsResult;
import com.philia.projectservice.catalog.api.ReplaceProjectTagsUseCase;
import com.philia.projectservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.CreateProjectWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectTagsWebMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectCommandControllerTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsCreatedApiResponseWithLocationAndEtag() {
        var result = result();
        CreateProjectUseCase useCase = command -> result;
        CreateProjectWebMapper createMapper = Mappers.getMapper(CreateProjectWebMapper.class);
        ProjectDetailWebMapper detailMapper = Mappers.getMapper(ProjectDetailWebMapper.class);
        ReplaceProjectTagsUseCase replaceTagsUseCase = command -> new ReplaceProjectTagsResult(
                command.projectId(), List.of(), command.expectedVersion() + 1);
        ProjectTagsWebMapper tagsMapper = Mappers.getMapper(ProjectTagsWebMapper.class);
        var controller = new ProjectCommandController(
                useCase, createMapper, detailMapper, replaceTagsUseCase, tagsMapper);
        var servletRequest = new MockHttpServletRequest("POST", "/api/v1/projects");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        var response = controller.createProject(new CreateProjectRequest(
                result.subCategory().id(),
                result.title(),
                result.slug(),
                result.shortDescription(),
                result.description(),
                result.demoUrl(),
                result.visibility(),
                result.techStack(),
                result.features(),
                result.tags().stream().map(ProjectDetailResult.Tag::id).toList()
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath())
                .isEqualTo("/api/v1/projects/" + result.id());
        assertThat(response.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo("PROJECT_CREATED");
        assertThat(response.getBody().data().id()).isEqualTo(result.id());
    }

    private static ProjectDetailResult result() {
        var now = Instant.parse("2026-07-24T02:00:00Z");
        return new ProjectDetailResult(
                UUID.randomUUID(),
                new ProjectDetailResult.Owner(UUID.randomUUID(), "Philia", null),
                new ProjectDetailResult.Category(UUID.randomUUID(), "software", "software", "Software", "code"),
                new ProjectDetailResult.SubCategory(UUID.randomUUID(), "backend", "backend", "Backend"),
                "Fiurozz Backend",
                "fiurozz-backend",
                "Short description",
                "Full description",
                null,
                "https://demo.example.com",
                List.of("java"),
                List.of("Project catalog"),
                List.of(new ProjectDetailResult.Tag(UUID.randomUUID(), "backend", "Backend")),
                "DRAFT",
                "PRIVATE",
                "HIDDEN",
                new ProjectDetailResult.Statistics(0, 0, 0),
                null,
                now,
                now,
                0
        );
    }
}
