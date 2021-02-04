package io.micronaut.data.hibernate.entities;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class EventTest {

    @Id
    @GeneratedValue
    private Long id;
    @AutoPopulated
    private UUID uuid;

    @DateCreated
    private LocalDateTime dateCreated;

    @DateUpdated
    private LocalDateTime dateUpdated;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
    public void prePersist() {
        prePersist++;
    }

    @Transient
    private int postPersist;
    @PostPersist
    void postPersist() {
        postPersist++;
    }

    @Transient
    private int preRemove;
    @PreRemove
    void preRemove() {
        preRemove++;
    }

    @Transient
    private int postRemove;
    @PostRemove
    void postRemove() {
        postRemove++;
    }

    @Transient
    private int preUpdate;
    @PreUpdate
    void preUpdate() {
        preUpdate++;
    }

    @Transient
    private int postUpdate;
    @PostUpdate
    void postUpdate() {
        postUpdate++;
    }

    @Transient
    private int postLoad;
    @PostLoad
    void postLoad() {
        postLoad++;
    }

    public int getPrePersist() {
        return prePersist;
    }

    public int getPostPersist() {
        return postPersist;
    }

    public int getPreRemove() {
        return preRemove;
    }

    public int getPostRemove() {
        return postRemove;
    }

    public int getPreUpdate() {
        return preUpdate;
    }

    public int getPostUpdate() {
        return postUpdate;
    }

    public int getPostLoad() {
        return postLoad;
    }
}
