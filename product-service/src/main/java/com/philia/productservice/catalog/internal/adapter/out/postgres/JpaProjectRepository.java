package com.philia.productservice.catalog.internal.adapter.out.postgres;

import com.philia.productservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.productservice.catalog.internal.application.port.out.ProjectRepository;
import com.philia.productservice.catalog.internal.domain.Project;
import com.philia.productservice.catalog.internal.domain.ProjectSlug;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JpaProjectRepository implements ProjectRepository {

    private final JpaProjectCommandRepository projectRepository;
    private final EntityManager entityManager;

    public JpaProjectRepository(
            JpaProjectCommandRepository projectRepository,
            EntityManager entityManager
    ) {
        this.projectRepository = projectRepository;
        this.entityManager = entityManager;
    }

    @Override
    public boolean existsActiveSlug(UUID ownerId, ProjectSlug slug) {
        return projectRepository.existsByOwnerIdAndSlugAndDeletedAtIsNull(ownerId, slug.value());
    }

    @Override
    public void save(Project project) {
        try {
            var subCategory = entityManager.getReference(ProjectSubCategoryJpaEntity.class, project.subCategoryId());
            var entity = ProjectJpaEntity.forCreate(project, subCategory);
            projectRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new ProjectSlugAlreadyExistsException(project.slug().value());
        }
    }
}
