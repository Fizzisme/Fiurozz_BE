package com.philia.productservice.catalog.internal.application.port.out;

import com.philia.productservice.catalog.internal.domain.Project;
import com.philia.productservice.catalog.internal.domain.ProjectSlug;

import java.util.UUID;

public interface ProjectRepository {

    boolean existsActiveSlug(UUID ownerId, ProjectSlug slug);

    void save(Project project);
}
