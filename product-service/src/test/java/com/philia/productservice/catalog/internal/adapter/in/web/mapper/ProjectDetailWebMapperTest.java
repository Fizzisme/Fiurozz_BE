package com.philia.productservice.catalog.internal.adapter.in.web.mapper;

import com.philia.productservice.catalog.api.ProjectDetailResult;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDetailWebMapperTest {

    private final ProjectDetailWebMapper mapper = Mappers.getMapper(ProjectDetailWebMapper.class);

    @Test
    void mapsApplicationResultToResponseDto() {
        var now = Instant.parse("2026-07-24T02:00:00Z");
        var result = new ProjectDetailResult(
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

        var response = mapper.toResponse(result);

        assertThat(response.id()).isEqualTo(result.id());
        assertThat(response.owner().displayName()).isEqualTo("Philia");
        assertThat(response.tags()).singleElement().satisfies(tag ->
                assertThat(tag.displayName()).isEqualTo("Backend")
        );
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.version()).isZero();
    }
}
