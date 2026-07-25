package com.philia.productservice.catalog.internal.domain;

import com.philia.productservice.catalog.internal.domain.exception.InvalidProjectException;

import java.util.Locale;
import java.util.regex.Pattern;

public record ProjectSlug(String value) {

    private static final int MAX_LENGTH = 180;
    private static final Pattern SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHEN = Pattern.compile("(^-+|-+$)");

    public ProjectSlug {
        if (value == null || value.isBlank()) {
            throw new InvalidProjectException("Project slug must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidProjectException("Project slug must not exceed 180 characters");
        }
        if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new InvalidProjectException("Project slug has an invalid format");
        }
    }

    public static ProjectSlug from(String rawValue) {
        if (rawValue == null) {
            throw new InvalidProjectException("Project slug must not be blank");
        }

        var normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        normalized = SEPARATOR.matcher(normalized).replaceAll("-");
        normalized = EDGE_HYPHEN.matcher(normalized).replaceAll("");
        return new ProjectSlug(normalized);
    }
}
