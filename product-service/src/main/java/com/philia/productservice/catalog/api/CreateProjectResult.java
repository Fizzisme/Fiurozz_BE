package com.philia.productservice.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateProjectResult(
        UUID id,
        Owner owner,
        Category category,
        SubCategory subCategory,
        String title,
        String slug,
        String shortDescription,
        String description,
        String thumbnailUrl,
        String demoUrl,
        List<String> techStack,
        List<String> features,
        List<Tag> tags,
        String status,
        String visibility,
        String sourceVisibility,
        Statistics statistics,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public record Owner(UUID id, String displayName, String avatarUrl) {
    }

    public record Category(UUID id, String key, String slug, String title, String icon) {
    }

    public record SubCategory(UUID id, String key, String slug, String title) {
    }

    public record Tag(UUID id, String slug, String displayName) {
    }

    public record Statistics(long viewCount, long likeCount, long commentCount) {
    }
}
