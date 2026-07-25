package com.philia.projectservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

interface JpaProjectTagReferenceRepository extends Repository<ProjectTagJpaEntity, UUID> {

    @Query("""
            SELECT tag
            FROM ProjectTagJpaEntity tag
            WHERE tag.id IN :tagIds
              AND tag.status = 'ACTIVE'
            ORDER BY tag.displayName ASC, tag.id ASC
            """)
    List<ProjectTagJpaEntity> findActiveByIds(@Param("tagIds") Set<UUID> tagIds);
}
