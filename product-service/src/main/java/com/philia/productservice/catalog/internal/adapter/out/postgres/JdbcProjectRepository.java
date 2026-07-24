package com.philia.productservice.catalog.internal.adapter.out.postgres;

import com.philia.productservice.catalog.internal.application.exception.ProjectSlugAlreadyExistsException;
import com.philia.productservice.catalog.internal.application.port.out.ProjectRepository;
import com.philia.productservice.catalog.internal.domain.Project;
import com.philia.productservice.catalog.internal.domain.ProjectSlug;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class JdbcProjectRepository implements ProjectRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcProjectRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsActiveSlug(UUID ownerId, ProjectSlug slug) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM projects
                            WHERE owner_id = :ownerId
                              AND slug = :slug
                              AND deleted_at IS NULL
                        )
                        """)
                .param("ownerId", ownerId)
                .param("slug", slug.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    public void save(Project project) {
        try {
            var rows = jdbcClient.sql("""
                            INSERT INTO projects (
                                id,
                                owner_id,
                                owner_display_name,
                                owner_avatar_url,
                                sub_category_id,
                                title,
                                slug,
                                short_description,
                                description,
                                demo_url,
                                tech_stack,
                                features,
                                status,
                                visibility,
                                source_visibility,
                                view_count,
                                like_count,
                                comment_count,
                                created_at,
                                updated_at,
                                row_version
                            ) VALUES (
                                :id,
                                :ownerId,
                                :ownerDisplayName,
                                :ownerAvatarUrl,
                                :subCategoryId,
                                :title,
                                :slug,
                                :shortDescription,
                                :description,
                                :demoUrl,
                                CAST(:techStack AS jsonb),
                                CAST(:features AS jsonb),
                                :status,
                                :visibility,
                                :sourceVisibility,
                                :viewCount,
                                :likeCount,
                                :commentCount,
                                :createdAt,
                                :updatedAt,
                                :rowVersion
                            )
                            """)
                    .param("id", project.id())
                    .param("ownerId", project.ownerId())
                    .param("ownerDisplayName", project.ownerDisplayName())
                    .param("ownerAvatarUrl", project.ownerAvatarUrl())
                    .param("subCategoryId", project.subCategoryId())
                    .param("title", project.title())
                    .param("slug", project.slug().value())
                    .param("shortDescription", project.shortDescription())
                    .param("description", project.description())
                    .param("demoUrl", project.demoUrl())
                    .param("techStack", objectMapper.writeValueAsString(project.techStack()))
                    .param("features", objectMapper.writeValueAsString(project.features()))
                    .param("status", project.status().name())
                    .param("visibility", project.visibility().name())
                    .param("sourceVisibility", project.sourceVisibility().name())
                    .param("viewCount", project.viewCount())
                    .param("likeCount", project.likeCount())
                    .param("commentCount", project.commentCount())
                    .param("createdAt", OffsetDateTime.ofInstant(project.createdAt(), ZoneOffset.UTC))
                    .param("updatedAt", OffsetDateTime.ofInstant(project.updatedAt(), ZoneOffset.UTC))
                    .param("rowVersion", project.version())
                    .update();

            if (rows != 1) {
                throw new IllegalStateException("Expected one Project row to be inserted but inserted " + rows);
            }
        } catch (DuplicateKeyException exception) {
            throw new ProjectSlugAlreadyExistsException(project.slug().value());
        }
    }
}
