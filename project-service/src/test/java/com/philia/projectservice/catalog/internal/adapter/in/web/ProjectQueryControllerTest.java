package com.philia.projectservice.catalog.internal.adapter.in.web;

import com.philia.projectservice.catalog.api.GetProjectByIdUseCase;
import com.philia.projectservice.catalog.api.ProjectDetailResult;
import com.philia.projectservice.catalog.internal.adapter.in.web.mapper.ProjectDetailWebMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectQueryControllerTest {

    @Test
    void returnsProjectInApiResponseWithCurrentEtag() {
        var result = project();
        GetProjectByIdUseCase useCase = projectId -> result;
        ProjectDetailWebMapper mapper = Mappers.getMapper(ProjectDetailWebMapper.class);
        var controller = new ProjectQueryController(useCase, mapper);

        var response = controller.getProjectById(result.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"2\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().code()).isEqualTo("PROJECT_RETRIEVED");
        assertThat(response.getBody().data().id()).isEqualTo(result.id());
    }

    private static ProjectDetailResult project() {
        var now = Instant.parse("2026-07-24T02:00:00Z");
        return new ProjectDetailResult(
                UUID.randomUUID(),
                new ProjectDetailResult.Owner(UUID.randomUUID(), "Owner", null),
                new ProjectDetailResult.Category(UUID.randomUUID(), "software", "software", "Software", "code"),
                new ProjectDetailResult.SubCategory(UUID.randomUUID(), "backend", "backend", "Backend"),
                "Fiurozz Backend",
                "fiurozz-backend",
                "Short description",
                "Full description",
                null,
                null,
                List.of("java"),
                List.of("Project catalog"),
                List.of(),
                "DRAFT",
                "PRIVATE",
                "HIDDEN",
                new ProjectDetailResult.Statistics(0, 0, 0),
                null,
                now,
                now,
                2
        );
    }
}
