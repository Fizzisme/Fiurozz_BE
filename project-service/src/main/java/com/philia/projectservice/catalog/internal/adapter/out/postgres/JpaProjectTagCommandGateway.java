package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagCommandGateway;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** JPA implementation of the persistence port used by ReplaceProjectTagsHandler. */
@Repository
public class JpaProjectTagCommandGateway implements ProjectTagCommandGateway {

    private final JpaProjectCommandRepository projectRepository;
    private final EntityManager entityManager;

    public JpaProjectTagCommandGateway(JpaProjectCommandRepository projectRepository, EntityManager entityManager) {
        this.projectRepository = projectRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ProjectState> findActiveState(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(project -> new ProjectState(project.getOwnerId(), project.getVersion()));
    }

    @Override
    public boolean advanceVersion(UUID projectId, UUID ownerId, long expectedVersion, Instant updatedAt) {
        // The repository update includes the old version in its WHERE clause.
        return projectRepository.advanceVersionIfCurrent(projectId, ownerId, expectedVersion, updatedAt) == 1;
    }

    @Override
    public void replaceTags(UUID projectId, Set<UUID> tagIds) {
        // Fetch current associations so Hibernate can delete removed join-table rows
        // and insert the supplied replacement rows in the same transaction.
        var project = entityManager.createQuery("""
                        SELECT DISTINCT project
                        FROM ProjectJpaEntity project
                        LEFT JOIN FETCH project.tags
                        WHERE project.id = :projectId
                          AND project.deletedAt IS NULL
                        """, ProjectJpaEntity.class)
                .setParameter("projectId", projectId)
                .getResultStream()
                .findFirst()
                .orElseThrow(ProjectNotFoundException::new);

        // Tag existence and ACTIVE status were validated in the application layer.
        var tags = tagIds.stream()
                .map(tagId -> entityManager.getReference(ProjectTagJpaEntity.class, tagId))
                .toList();
        project.replaceTags(tags);
        entityManager.flush();
    }
}
