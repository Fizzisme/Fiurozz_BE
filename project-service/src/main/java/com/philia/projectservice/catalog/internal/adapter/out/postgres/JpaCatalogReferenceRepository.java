package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface JpaCatalogReferenceRepository extends Repository<ProjectSubCategoryJpaEntity, UUID> {

    @Query("""
            SELECT subCategory
            FROM ProjectSubCategoryJpaEntity subCategory
            JOIN FETCH subCategory.category category
            WHERE subCategory.id = :subCategoryId
              AND subCategory.active = TRUE
              AND subCategory.deletedAt IS NULL
              AND category.active = TRUE
              AND category.deletedAt IS NULL
            """)
    Optional<ProjectSubCategoryJpaEntity> findActiveWithCategoryById(@Param("subCategoryId") UUID subCategoryId);
}
