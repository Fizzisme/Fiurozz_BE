package com.philia.productservice.catalog.internal.application;

import com.philia.productservice.catalog.api.ProjectDetailResult;
import com.philia.productservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.productservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.productservice.catalog.internal.application.port.out.ProjectDetailQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProjectByIdHandlerTest {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void allowsOwnerToReadDraftProject() {
        var expected = project("DRAFT", "PRIVATE");
        var handler = handler(Optional.of(expected), Optional.of(actor(OWNER_ID)));

        var result = handler.getProject(PROJECT_ID);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void allowsAnonymousCallerToReadPublishedUnlistedProject() {
        var expected = project("PUBLISHED", "UNLISTED");
        var handler = handler(Optional.of(expected), Optional.empty());

        var result = handler.getProject(PROJECT_ID);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void hidesPrivateProjectFromAnotherUser() {
        var handler = handler(
                Optional.of(project("PUBLISHED", "PRIVATE")),
                Optional.of(actor(OTHER_USER_ID))
        );

        assertThatThrownBy(() -> handler.getProject(PROJECT_ID))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project not found.");
    }

    @Test
    void returnsNotFoundWhenActiveProjectDoesNotExist() {
        var handler = handler(Optional.empty(), Optional.of(actor(OWNER_ID)));

        assertThatThrownBy(() -> handler.getProject(PROJECT_ID))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    private static GetProjectByIdHandler handler(
            Optional<ProjectDetailResult> project,
            Optional<CurrentActor.Actor> actor
    ) {
        ProjectDetailQuery query = projectId -> project;
        CurrentActor currentActor = () -> actor;
        return new GetProjectByIdHandler(query, currentActor);
    }

    private static CurrentActor.Actor actor(UUID id) {
        return new CurrentActor.Actor(id, "Viewer", null);
    }

    private static ProjectDetailResult project(String status, String visibility) {
        var now = Instant.parse("2026-07-24T02:00:00Z");
        return new ProjectDetailResult(
                PROJECT_ID,
                new ProjectDetailResult.Owner(OWNER_ID, "Owner", null),
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
                status,
                visibility,
                "HIDDEN",
                new ProjectDetailResult.Statistics(0, 0, 0),
                "PUBLISHED".equals(status) ? now : null,
                now,
                now,
                0
        );
    }
}
