package com.philia.productservice.catalog.internal.adapter.out.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "project_categories")
public class ProjectCategoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "key")
    private String key;

    private String slug;
    private String title;
    private String icon;

    protected ProjectCategoryJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }
}
