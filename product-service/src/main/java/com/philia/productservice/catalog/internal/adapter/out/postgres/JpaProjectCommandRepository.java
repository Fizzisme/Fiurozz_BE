package com.philia.productservice.catalog.internal.adapter.out.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface JpaProjectCommandRepository extends JpaRepository<ProjectJpaEntity, UUID> {

    boolean existsByOwnerIdAndSlugAndDeletedAtIsNull(UUID ownerId, String slug);
}
