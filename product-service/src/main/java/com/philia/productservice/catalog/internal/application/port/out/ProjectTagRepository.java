package com.philia.productservice.catalog.internal.application.port.out;

import java.util.Set;
import java.util.UUID;

public interface ProjectTagRepository {

    void addAll(UUID projectId, Set<UUID> tagIds);
}
