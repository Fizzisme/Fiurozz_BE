package com.philia.productservice.catalog.internal.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CatalogReferenceQuery {

    Optional<SubCategoryReference> findActiveSubCategory(UUID subCategoryId);

    List<TagReference> findActiveTags(Set<UUID> tagIds);

    record CategoryReference(UUID id, String key, String slug, String title, String icon) {
    }

    record SubCategoryReference(
            UUID id,
            String key,
            String slug,
            String title,
            CategoryReference category
    ) {
    }

    record TagReference(UUID id, String slug, String displayName) {
    }
}
