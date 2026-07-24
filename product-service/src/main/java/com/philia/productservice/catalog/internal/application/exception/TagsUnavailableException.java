package com.philia.productservice.catalog.internal.application.exception;

import java.util.Set;
import java.util.UUID;

public final class TagsUnavailableException extends RuntimeException {

    private final Set<UUID> tagIds;

    public TagsUnavailableException(Set<UUID> tagIds) {
        super("Tags are missing or inactive: " + tagIds);
        this.tagIds = Set.copyOf(tagIds);
    }

    public Set<UUID> tagIds() {
        return tagIds;
    }
}
