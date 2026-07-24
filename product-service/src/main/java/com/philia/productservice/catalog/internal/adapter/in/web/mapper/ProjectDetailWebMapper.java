package com.philia.productservice.catalog.internal.adapter.in.web.mapper;

import com.philia.productservice.catalog.api.ProjectDetailResult;
import com.philia.productservice.catalog.internal.adapter.in.web.dto.response.ProjectDetailResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Maps the application projection to the HTTP response without exposing persistence models.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProjectDetailWebMapper {

    ProjectDetailResponse toResponse(ProjectDetailResult result);
}
