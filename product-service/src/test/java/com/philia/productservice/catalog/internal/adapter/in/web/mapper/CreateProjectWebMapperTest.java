package com.philia.productservice.catalog.internal.adapter.in.web.mapper;

import com.philia.productservice.catalog.api.CreateProjectResult;
import com.philia.productservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateProjectWebMapperTest {

    private final CreateProjectWebMapper mapper = Mappers.getMapper(CreateProjectWebMapper.class);

    @Test
    void mapsRequestToApplicationCommand() {
        var subCategoryId = UUID.randomUUID();
        var tagId = UUID.randomUUID();
        var request = new CreateProjectRequest(
                subCategoryId,
                "Fiurozz Backend",
                "fiurozz-backend",
                "Short description",
                "Full description",
                "https://demo.example.com",
                "PRIVATE",
                List.of("java"),
                List.of("Project catalog"),
                List.of(tagId)
        );

        var command = mapper.toCommand(request);

        assertThat(command.subCategoryId()).isEqualTo(subCategoryId);
        assertThat(command.title()).isEqualTo("Fiurozz Backend");
        assertThat(command.tagIds()).containsExactly(tagId);
    }

    @Test
    void mapsApplicationResultToResponseDto() {
        var now = Instant.parse("2026-07-24T02:00:00Z");
        var result = new CreateProjectResult(
                UUID.randomUUID(),
                new CreateProjectResult.Owner(UUID.randomUUID(), "Philia", null),
                new CreateProjectResult.Category(UUID.randomUUID(), "software", "software", "Software", "code"),
                new CreateProjectResult.SubCategory(UUID.randomUUID(), "backend", "backend", "Backend"),
                "Fiurozz Backend",
                "fiurozz-backend",
                "Short description",
                "Full description",
                null,
                "https://demo.example.com",
                List.of("java"),
                List.of("Project catalog"),
                List.of(new CreateProjectResult.Tag(UUID.randomUUID(), "backend", "Backend")),
                "DRAFT",
                "PRIVATE",
                "HIDDEN",
                new CreateProjectResult.Statistics(0, 0, 0),
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
