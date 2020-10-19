package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;

import java.io.Serializable;
import java.time.LocalDateTime;

public abstract class BaseEntity<ID extends Serializable> implements Serializable {

    @Id
    @GeneratedValue
    private ID id;
    @DateCreated
    private LocalDateTime createdDate;
    @DateUpdated
    private LocalDateTime updatedDate;

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
}
