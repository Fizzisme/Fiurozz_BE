package com.philia.productservice.catalog.api;

public interface CreateProjectUseCase {

    ProjectDetailResult create(CreateProjectCommand command);
}
