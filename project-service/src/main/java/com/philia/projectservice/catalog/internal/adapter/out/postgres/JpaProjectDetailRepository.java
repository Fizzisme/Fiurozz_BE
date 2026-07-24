package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

interface JpaProjectDetailRepository extends Repository<ProjectJpaEntity, UUID> {

    @Query("""
            SELECT DISTINCT project
            FROM ProjectJpaEntity project
            JOIN FETCH project.subCategory subCategory
            JOIN FETCH subCategory.category category
            LEFT JOIN FETCH project.tags tag
            WHERE project.id = :projectId
              AND project.deletedAt IS NULL
            """)
    Optional<ProjectJpaEntity> findActiveDetailById(@Param("projectId") UUID projectId);
}
