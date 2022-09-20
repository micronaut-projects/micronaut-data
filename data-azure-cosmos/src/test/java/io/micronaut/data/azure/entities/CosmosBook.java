/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.azure.entities;

import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.event.PostLoad;
import io.micronaut.data.annotation.event.PostPersist;
import io.micronaut.data.annotation.event.PostRemove;
import io.micronaut.data.annotation.event.PostUpdate;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.annotation.event.PreRemove;
import io.micronaut.data.annotation.event.PreUpdate;
import io.micronaut.data.cosmos.annotation.Container;
import io.micronaut.data.cosmos.annotation.PartitionKey;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Container(throughputRequestUnits = 1000, throughputAutoScale = true, partitionKeyPath = "title")
@MappedEntity
public class CosmosBook {
    @Id
    @GeneratedValue
    @PartitionKey
    private String id;
    private String title;
    private int totalPages;
    @Version
    @MappedProperty(value = "_etag")
    private String version;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private CosmosAuthor author;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "book", cascade = Relation.Cascade.ALL)
    private List<CosmosPage> pages = new ArrayList<>();

    @Transient
    public int prePersist, postPersist, preUpdate, postUpdate, preRemove, postRemove, postLoad;

    @DateUpdated
    private LocalDateTime created;

    @DateUpdated
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onPrePersist() {
        prePersist++;
    }

    @PostPersist
    protected void onPostPersist() {
        postPersist++;
    }

    @PreUpdate
    protected void onPreUpdate() {
        preUpdate++;
    }

    @PostUpdate
    protected void onPostUpdate() {
        postUpdate++;
    }

    @PreRemove
    protected void onPreRemove() {
        preRemove++;
    }

    @PostRemove
    protected void onPostRemove() {
        postRemove++;
    }

    @PostLoad
    protected void onPostLoad() {
        postLoad++;
    }

    @Transient
    public void resetEventCounters() {
        prePersist = 0;
        postPersist = 0;
        preUpdate = 0;
        postUpdate = 0;
        preRemove = 0;
        postRemove = 0;
        postLoad = 0;
    }

    public List<CosmosPage> getPages() {
        return pages;
    }

    public void setPages(List<CosmosPage> pages) {
        this.pages = pages;
    }

    public CosmosAuthor getAuthor() {
        return author;
    }

    public void setAuthor(CosmosAuthor author) {
        this.author = author;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
