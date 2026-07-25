package com.philia.projectservice.catalog.internal.application.exception;

public class ProjectForbiddenException extends RuntimeException {

    public ProjectForbiddenException() {
        super("You do not have permission to modify this project.");
    }
}
