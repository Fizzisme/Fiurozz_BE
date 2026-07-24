package com.philia.productservice.catalog.internal.adapter.out.postgres;

import com.philia.productservice.catalog.api.ProjectDetailResult;
import com.philia.productservice.catalog.internal.application.port.out.ProjectDetailQuery;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA read adapter for the complete project detail projection.
 */
@Repository
public class JpaProjectDetailQuery implements ProjectDetailQuery {

    private final JpaProjectDetailRepository projectRepository;
    private final ObjectMapper objectMapper;

    public JpaProjectDetailQuery(JpaProjectDetailRepository projectRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProjectDetailResult> findActiveById(UUID projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        return projectRepository.findActiveDetailById(projectId).map(this::toResult);
    }

    private ProjectDetailResult toResult(ProjectJpaEntity project) {
        var subCategory = project.getSubCategory();
        var category = subCategory.getCategory();
        var tags = project.getTags().stream()
                .map(tag -> new ProjectDetailResult.Tag(tag.getId(), tag.getSlug(), tag.getDisplayName()))
                .sorted(Comparator.comparing(ProjectDetailResult.Tag::displayName)
                        .thenComparing(ProjectDetailResult.Tag::id))
                .toList();

        return new ProjectDetailResult(
                project.getId(),
                new ProjectDetailResult.Owner(
                        project.getOwnerId(), project.getOwnerDisplayName(), project.getOwnerAvatarUrl()),
                new ProjectDetailResult.Category(
                        category.getId(), category.getKey(), category.getSlug(), category.getTitle(), category.getIcon()),
                new ProjectDetailResult.SubCategory(
                        subCategory.getId(), subCategory.getKey(), subCategory.getSlug(), subCategory.getTitle()),
                project.getTitle(),
                project.getSlug(),
                project.getShortDescription(),
                project.getDescription(),
                project.getThumbnailUrl(),
                project.getDemoUrl(),
                readStringList(project.getTechStackJson()),
                readStringList(project.getFeaturesJson()),
                tags,
                project.getStatus(),
                project.getVisibility(),
                project.getSourceVisibility(),
                new ProjectDetailResult.Statistics(
                        project.getViewCount(), project.getLikeCount(), project.getCommentCount()),
                project.getPublishedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion()
        );
    }

    private List<String> readStringList(String json) {
        return List.copyOf(Arrays.asList(objectMapper.readValue(json, String[].class)));
    }
}
