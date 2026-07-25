package com.philia.productservice.catalog.internal.adapter.out.postgres;

import com.philia.productservice.catalog.internal.application.port.out.CatalogReferenceQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JpaCatalogReferenceQuery implements CatalogReferenceQuery {

    private final JpaCatalogReferenceRepository catalogRepository;
    private final JpaProjectTagReferenceRepository tagRepository;

    public JpaCatalogReferenceQuery(
            JpaCatalogReferenceRepository catalogRepository,
            JpaProjectTagReferenceRepository tagRepository
    ) {
        this.catalogRepository = catalogRepository;
        this.tagRepository = tagRepository;
    }

    @Override
    public Optional<SubCategoryReference> findActiveSubCategory(UUID subCategoryId) {
        if (subCategoryId == null) {
            return Optional.empty();
        }
        return catalogRepository.findActiveWithCategoryById(subCategoryId).map(subCategory -> {
            var category = subCategory.getCategory();
            return new SubCategoryReference(
                    subCategory.getId(),
                    subCategory.getKey(),
                    subCategory.getSlug(),
                    subCategory.getTitle(),
                    new CategoryReference(
                            category.getId(),
                            category.getKey(),
                            category.getSlug(),
                            category.getTitle(),
                            category.getIcon()
                    )
            );
        });
    }

    @Override
    public List<TagReference> findActiveTags(Set<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findActiveByIds(tagIds).stream()
                .map(tag -> new TagReference(tag.getId(), tag.getSlug(), tag.getDisplayName()))
                .toList();
    }
}
