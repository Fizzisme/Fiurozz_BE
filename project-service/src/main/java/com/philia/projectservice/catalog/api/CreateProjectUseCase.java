package com.philia.projectservice.catalog.api;

public interface CreateProjectUseCase {

    ProjectDetailResult create(CreateProjectCommand command);
}
