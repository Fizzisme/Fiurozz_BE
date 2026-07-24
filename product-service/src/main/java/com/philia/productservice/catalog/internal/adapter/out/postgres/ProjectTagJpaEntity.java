package com.philia.productservice.catalog.internal.adapter.out.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "tags")
public class ProjectTagJpaEntity {

    @Id
    private UUID id;

    private String slug;

    @Column(name = "display_name")
    private String displayName;

    protected ProjectTagJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }
}
