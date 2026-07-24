package com.philia.projectservice.catalog.api;

import java.util.UUID;

/**
 * Input port for retrieving one project while applying the caller's access rules.
 */
public interface GetProjectByIdUseCase {

    /**
     * Returns the project visible to the current caller.
     *
     * @param projectId project identifier
     * @return complete project detail projection
     * @throws com.philia.projectservice.catalog.internal.application.exception.ProjectNotFoundException
     *         when the project is missing, deleted, or inaccessible
     */
    ProjectDetailResult getProject(UUID projectId);
}
