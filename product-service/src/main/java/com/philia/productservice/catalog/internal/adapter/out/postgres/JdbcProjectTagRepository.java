package com.philia.productservice.catalog.internal.adapter.out.postgres;

import com.philia.productservice.catalog.internal.application.port.out.ProjectTagRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcProjectTagRepository implements ProjectTagRepository {

    private final JdbcClient jdbcClient;

    public JdbcProjectTagRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void addAll(UUID projectId, Set<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        for (var tagId : tagIds) {
            jdbcClient.sql("""
                            INSERT INTO project_tags (project_id, tag_id)
                            VALUES (:projectId, :tagId)
                            """)
                    .param("projectId", projectId)
                    .param("tagId", tagId)
                    .update();
        }
    }
}
