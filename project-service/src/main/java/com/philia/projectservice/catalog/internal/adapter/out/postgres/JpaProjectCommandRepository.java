package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface JpaProjectCommandRepository extends JpaRepository<ProjectJpaEntity, UUID> {

    boolean existsByOwnerIdAndSlugAndDeletedAtIsNull(UUID ownerId, String slug);

    Optional<ProjectJpaEntity> findByIdAndDeletedAtIsNull(UUID projectId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectJpaEntity project
            SET project.version = project.version + 1,
                project.updatedAt = :updatedAt
            WHERE project.id = :projectId
              AND project.ownerId = :ownerId
              AND project.version = :expectedVersion
              AND project.deletedAt IS NULL
            """)
    int advanceVersionIfCurrent(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") Instant updatedAt
    );
}
