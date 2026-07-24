package com.philia.productservice.catalog.internal.adapter.out.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "project_sub_categories")
public class ProjectSubCategoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "key")
    private String key;

    private String slug;
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProjectCategoryJpaEntity category;

    protected ProjectSubCategoryJpaEntity() {
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

    public ProjectCategoryJpaEntity getCategory() {
        return category;
    }
}
