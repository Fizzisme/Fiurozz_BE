package com.philia.projectservice.catalog.internal.application;

import com.philia.projectservice.catalog.api.ReplaceProjectTagsCommand;
import com.philia.projectservice.catalog.internal.application.exception.ProjectForbiddenException;
import com.philia.projectservice.catalog.internal.application.exception.ProjectStaleVersionException;
import com.philia.projectservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagCommandGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplaceProjectTagsHandlerTest {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TAG_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void replacesTagsAndIncrementsTheVersion() {
        var gateway = new FakeProjectTagCommandGateway(OWNER_ID, 3);
        var handler = handler(OWNER_ID, gateway);

        var result = handler.replaceProjectTags(new ReplaceProjectTagsCommand(PROJECT_ID, 3, List.of(TAG_ID, TAG_ID)));

        assertThat(result.version()).isEqualTo(4);
        assertThat(result.tags()).singleElement().satisfies(tag -> assertThat(tag.id()).isEqualTo(TAG_ID));
        assertThat(gateway.replacedTags).containsExactly(TAG_ID);
    }

    @Test
    void rejectsAStaleVersionBeforeChangingTags() {
        var gateway = new FakeProjectTagCommandGateway(OWNER_ID, 3);
        var handler = handler(OWNER_ID, gateway);

        assertThatThrownBy(() -> handler.replaceProjectTags(new ReplaceProjectTagsCommand(PROJECT_ID, 2, List.of(TAG_ID))))
                .isInstanceOf(ProjectStaleVersionException.class);
        assertThat(gateway.replacedTags).isEmpty();
    }

    @Test
    void rejectsAnotherOwnerBeforeChangingTags() {
        var gateway = new FakeProjectTagCommandGateway(OWNER_ID, 3);
        var handler = handler(OTHER_USER_ID, gateway);

        assertThatThrownBy(() -> handler.replaceProjectTags(new ReplaceProjectTagsCommand(PROJECT_ID, 3, List.of(TAG_ID))))
                .isInstanceOf(ProjectForbiddenException.class);
        assertThat(gateway.replacedTags).isEmpty();
    }

    private static ReplaceProjectTagsHandler handler(UUID actorId, FakeProjectTagCommandGateway gateway) {
        CurrentActor actor = () -> Optional.of(new CurrentActor.Actor(actorId, "Philia", null));
        CatalogReferenceQuery catalog = new CatalogReferenceQuery() {
            @Override
            public Optional<SubCategoryReference> findActiveSubCategory(UUID subCategoryId) {
                return Optional.empty();
            }

            @Override
            public List<TagReference> findActiveTags(Set<UUID> tagIds) {
                return tagIds.stream().map(id -> new TagReference(id, "backend", "Backend")).toList();
            }
        };
        return new ReplaceProjectTagsHandler(
                actor, catalog, gateway, Clock.fixed(Instant.parse("2026-07-24T02:00:00Z"), ZoneOffset.UTC));
    }

    private static final class FakeProjectTagCommandGateway implements ProjectTagCommandGateway {

        private final UUID ownerId;
        private final long version;
        private Set<UUID> replacedTags = Set.of();

        private FakeProjectTagCommandGateway(UUID ownerId, long version) {
            this.ownerId = ownerId;
            this.version = version;
        }

        @Override
        public Optional<ProjectState> findActiveState(UUID projectId) {
            return Optional.of(new ProjectState(ownerId, version));
        }

        @Override
        public boolean advanceVersion(UUID projectId, UUID ownerId, long expectedVersion, Instant updatedAt) {
            return this.ownerId.equals(ownerId) && version == expectedVersion;
        }

        @Override
        public void replaceTags(UUID projectId, Set<UUID> tagIds) {
            replacedTags = Set.copyOf(tagIds);
        }
    }
}
