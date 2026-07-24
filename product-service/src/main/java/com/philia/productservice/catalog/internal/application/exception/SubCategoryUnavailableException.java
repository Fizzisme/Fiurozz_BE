package com.philia.productservice.catalog.internal.application.exception;

import java.util.UUID;

public final class SubCategoryUnavailableException extends RuntimeException {

    public SubCategoryUnavailableException(UUID subCategoryId) {
        super("Subcategory is missing, inactive, or deleted: " + subCategoryId);
    }
}
