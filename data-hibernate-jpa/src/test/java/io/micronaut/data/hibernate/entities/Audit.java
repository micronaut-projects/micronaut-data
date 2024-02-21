package io.micronaut.data.hibernate.entities;

import io.micronaut.data.annotation.Embeddable;

import java.time.Instant;

@Embeddable
public class Audit {

    private Instant createdTime = Instant.now();

    private String createdBy = "current";

    public Instant getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
