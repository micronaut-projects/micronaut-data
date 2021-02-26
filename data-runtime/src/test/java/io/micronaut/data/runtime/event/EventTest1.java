package io.micronaut.data.runtime.event;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.event.PostLoad;
import io.micronaut.data.annotation.event.PostPersist;
import io.micronaut.data.annotation.event.PostRemove;
import io.micronaut.data.annotation.event.PostUpdate;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreRemove;
import io.micronaut.data.annotation.event.PreUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedEntity
class EventTest1 {
    @Id
    @AutoPopulated
    UUID uuid;

    @DateCreated
    LocalDateTime dateCreated;

    @DateUpdated
    LocalDateTime dateUpdated;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public LocalDateTime getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(LocalDateTime dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    private int prePersist;
    @PrePersist
    void prePersist() {
        prePersist++;
    }

    private int postPersist;
    @PostPersist
    void postPersist() {
        postPersist++;
    }

    private int preRemove;
    @PreRemove
    void preRemove() {
        preRemove++;
    }

    private int postRemove;
    @PostRemove
    void postRemove() {
        postRemove++;
    }

    private int preUpdate;
    @PreUpdate
    void preUpdate() {
        preUpdate++;
    }

    private int postUpdate;
    @PostUpdate
    void postUpdate() {
        postUpdate++;
    }

    private int postLoad;
    @PostLoad
    void postLoad() {
        postLoad++;
    }
}