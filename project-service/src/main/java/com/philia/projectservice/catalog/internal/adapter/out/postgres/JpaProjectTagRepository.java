package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import com.philia.projectservice.catalog.internal.application.port.out.ProjectTagRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public class JpaProjectTagRepository implements ProjectTagRepository {

    private final EntityManager entityManager;

    public JpaProjectTagRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void addAll(UUID projectId, Set<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        var project = entityManager.getReference(ProjectJpaEntity.class, projectId);
        tagIds.forEach(tagId -> project.addTag(entityManager.getReference(ProjectTagJpaEntity.class, tagId)));
        entityManager.flush();
    }
}
