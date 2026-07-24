package com.philia.productservice.catalog.internal.adapter.in.web.mapper;

import com.philia.productservice.catalog.api.CreateProjectCommand;
import com.philia.productservice.catalog.api.CreateProjectResult;
import com.philia.productservice.catalog.internal.adapter.in.web.dto.request.CreateProjectRequest;
import com.philia.productservice.catalog.internal.adapter.in.web.dto.response.CreateProjectResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CreateProjectWebMapper {

    CreateProjectCommand toCommand(CreateProjectRequest request);

    CreateProjectResponse toResponse(CreateProjectResult result);
}
