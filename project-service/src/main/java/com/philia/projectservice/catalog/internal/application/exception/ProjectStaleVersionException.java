package com.philia.projectservice.catalog.internal.application.exception;

public class ProjectStaleVersionException extends RuntimeException {

    public ProjectStaleVersionException() {
        super("The project was changed by another request. Reload it and try again.");
    }
}
