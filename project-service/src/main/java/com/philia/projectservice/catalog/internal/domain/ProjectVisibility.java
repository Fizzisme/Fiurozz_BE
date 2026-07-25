package com.philia.projectservice.catalog.internal.domain;

import com.philia.projectservice.catalog.internal.domain.exception.InvalidProjectException;

import java.util.Locale;

public enum ProjectVisibility {
    PUBLIC,
    UNLISTED,
    PRIVATE;

    public static ProjectVisibility fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidProjectException("Unsupported project visibility: " + value);
        }
    }
}
