package com.philia.productservice.catalog.internal.adapter.out.postgres;

import com.philia.productservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcCatalogReferenceQuery implements CatalogReferenceQuery {

    private final JdbcClient jdbcClient;

    public JdbcCatalogReferenceQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<SubCategoryReference> findActiveSubCategory(UUID subCategoryId) {
        if (subCategoryId == null) {
            return Optional.empty();
        }

        return jdbcClient.sql("""
                        SELECT
                            sc.id AS sub_category_id,
                            sc.key AS sub_category_key,
                            sc.slug AS sub_category_slug,
                            sc.title AS sub_category_title,
                            c.id AS category_id,
                            c.key AS category_key,
                            c.slug AS category_slug,
                            c.title AS category_title,
                            c.icon AS category_icon
                        FROM project_sub_categories sc
                        JOIN project_categories c ON c.id = sc.category_id
                        WHERE sc.id = :subCategoryId
                          AND sc.is_active = TRUE
                          AND sc.deleted_at IS NULL
                          AND c.is_active = TRUE
                          AND c.deleted_at IS NULL
                        """)
                .param("subCategoryId", subCategoryId)
                .query((resultSet, rowNumber) -> new SubCategoryReference(
                        resultSet.getObject("sub_category_id", UUID.class),
                        resultSet.getString("sub_category_key"),
                        resultSet.getString("sub_category_slug"),
                        resultSet.getString("sub_category_title"),
                        new CategoryReference(
                                resultSet.getObject("category_id", UUID.class),
                                resultSet.getString("category_key"),
                                resultSet.getString("category_slug"),
                                resultSet.getString("category_title"),
                                resultSet.getString("category_icon")
                        )
                ))
                .optional();
    }

    @Override
    public List<TagReference> findActiveTags(Set<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }

        return jdbcClient.sql("""
                        SELECT id, slug, display_name
                        FROM tags
                        WHERE id IN (:tagIds)
                          AND status = 'ACTIVE'
                        ORDER BY display_name ASC, id ASC
                        """)
                .param("tagIds", tagIds)
                .query((resultSet, rowNumber) -> new TagReference(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("slug"),
                        resultSet.getString("display_name")
                ))
                .list();
    }
}
