package com.philia.productservice.catalog.internal.application;

import com.philia.productservice.catalog.api.GetProjectByIdUseCase;
import com.philia.productservice.catalog.api.ProjectDetailResult;
import com.philia.productservice.catalog.internal.application.exception.ProjectNotFoundException;
import com.philia.productservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.productservice.catalog.internal.application.port.out.ProjectDetailQuery;
import com.philia.productservice.catalog.internal.domain.ProjectStatus;
import com.philia.productservice.catalog.internal.domain.ProjectVisibility;
import com.philia.productservice.catalog.internal.domain.exception.InvalidProjectException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Coordinates project retrieval and enforces owner/public visibility rules.
 */
@Service
public class GetProjectByIdHandler implements GetProjectByIdUseCase {

    private final ProjectDetailQuery projectDetailQuery;
    private final CurrentActor currentActor;

    public GetProjectByIdHandler(ProjectDetailQuery projectDetailQuery, CurrentActor currentActor) {
        this.projectDetailQuery = projectDetailQuery;
        this.currentActor = currentActor;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResult getProject(UUID projectId) {
        if (projectId == null) {
            throw new InvalidProjectException("Project ID is required.");
        }

        var project = projectDetailQuery.findActiveById(projectId)
                .orElseThrow(ProjectNotFoundException::new);
        var viewerId = currentActor.findActor().map(CurrentActor.Actor::id).orElse(null);

        // Return the same 404 for missing and forbidden projects so private project IDs
        // cannot be discovered by comparing API responses.
        if (!isOwner(project, viewerId) && !isPubliclyReadable(project)) {
            throw new ProjectNotFoundException();
        }
        return project;
    }

    private static boolean isOwner(ProjectDetailResult project, UUID viewerId) {
        return viewerId != null && viewerId.equals(project.owner().id());
    }

    private static boolean isPubliclyReadable(ProjectDetailResult project) {
        // UNLISTED projects are intentionally readable through direct lookup, but they
        // will be excluded from the public search endpoint.
        if (!ProjectStatus.PUBLISHED.name().equals(project.status())) {
            return false;
        }
        return ProjectVisibility.PUBLIC.name().equals(project.visibility())
                || ProjectVisibility.UNLISTED.name().equals(project.visibility());
    }
}
