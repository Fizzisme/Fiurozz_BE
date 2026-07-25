package com.philia.productservice.catalog.internal.application.exception;

public final class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException() {
        super("Project not found.");
    }
}
