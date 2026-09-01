/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.tck.jdbc.entities.upsert;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

@MappedEntity("auto_populated_upsert")
public class AutoPopulatedUpsertEntity {

    @Id
    private Long id;

    private String name;

    @DateCreated
    private LocalDateTime created;

    @DateUpdated
    private LocalDateTime updated;

    @Nullable
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.UPDATE)
    private ProductReview review;

    @Transient
    private int prePersistCalls;

    @Transient
    private int preUpdateCalls;

    @Transient
    private int postPersistCalls;

    @Transient
    private int postUpdateCalls;

    public AutoPopulatedUpsertEntity() {
    }

    public AutoPopulatedUpsertEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    @PrePersist
    void prePersist() {
        prePersistCalls++;
    }

    @PreUpdate
    void preUpdate() {
        preUpdateCalls++;
    }

    @PostPersist
    void postPersist() {
        postPersistCalls++;
    }

    @PostUpdate
    void postUpdate() {
        postUpdateCalls++;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }

    @Nullable
    public ProductReview getReview() {
        return review;
    }

    public void setReview(@Nullable ProductReview review) {
        this.review = review;
    }

    public int getPrePersistCalls() {
        return prePersistCalls;
    }

    public int getPreUpdateCalls() {
        return preUpdateCalls;
    }

    public int getPostPersistCalls() {
        return postPersistCalls;
    }

    public int getPostUpdateCalls() {
        return postUpdateCalls;
    }
}
