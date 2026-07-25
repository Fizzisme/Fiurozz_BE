package com.philia.projectservice.catalog.internal.application.exception;

public final class ProjectSlugAlreadyExistsException extends RuntimeException {

    public ProjectSlugAlreadyExistsException(String slug) {
        super("An active project already uses the slug: " + slug);
    }
}
