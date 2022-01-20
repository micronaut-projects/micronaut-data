/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.document.tck.entities;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.event.PostLoad;
import io.micronaut.data.annotation.event.PostPersist;
import io.micronaut.data.annotation.event.PostRemove;
import io.micronaut.data.annotation.event.PostUpdate;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreRemove;
import io.micronaut.data.annotation.event.PreUpdate;

import java.time.LocalDateTime;

@MappedEntity
public class DomainEvents {
    @Id
    @GeneratedValue
    String id;

    @DateCreated
    LocalDateTime dateCreated;

    @DateUpdated
    LocalDateTime dateUpdated;

    private String name = "test";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    private int postPersist;
    @PostPersist
    public void postPersist() {
        postPersist++;
    }

    private int preRemove;
    @PreRemove
    public void preRemove() {
        preRemove++;
    }

    private int postRemove;
    @PostRemove
    public void postRemove() {
        postRemove++;
    }

    private int preUpdate;
    @PreUpdate
    public void preUpdate() {
        preUpdate++;
    }

    private int postUpdate;
    @PostUpdate
    public void postUpdate() {
        postUpdate++;
    }

    private int postLoad;
    @PostLoad
    public void postLoad() {
        postLoad++;
    }

    @Transient
    public int getPrePersist() {
        return prePersist;
    }

    @Transient
    public int getPostPersist() {
        return postPersist;
    }

    @Transient
    public int getPreRemove() {
        return preRemove;
    }

    @Transient
    public int getPostRemove() {
        return postRemove;
    }

    @Transient
    public int getPreUpdate() {
        return preUpdate;
    }

    @Transient
    public int getPostUpdate() {
        return postUpdate;
    }

    @Transient
    public int getPostLoad() {
        return postLoad;
    }
}