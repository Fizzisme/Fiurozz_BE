package com.philia.productservice.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application-owned project detail projection shared by create and read use cases.
 * Keeping it independent from HTTP DTOs allows other input adapters to reuse it.
 */
public record ProjectDetailResult(
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

    public ProjectDetailResult {
        techStack = List.copyOf(techStack);
        features = List.copyOf(features);
        tags = List.copyOf(tags);
    }

    /**
     * Completes the base project projection with tags loaded by a separate query.
     */
    public ProjectDetailResult withTags(List<Tag> resolvedTags) {
        return new ProjectDetailResult(
                id,
                owner,
                category,
                subCategory,
                title,
                slug,
                shortDescription,
                description,
                thumbnailUrl,
                demoUrl,
                techStack,
                features,
                resolvedTags,
                status,
                visibility,
                sourceVisibility,
                statistics,
                publishedAt,
                createdAt,
                updatedAt,
                version
        );
    }

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
