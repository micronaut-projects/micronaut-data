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
package io.micronaut.data.tck.entities.schema;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity with associations used to validate generated foreign keys, join tables and embedded values.
 */
@MappedEntity("schema_book")
public class SchemaBook {

    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @Nullable
    private Duration readingTime;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private SchemaAuthor author;

    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    private SchemaAuthor editor;

    @Relation(Relation.Kind.MANY_TO_MANY)
    private Set<SchemaTag> tags = new HashSet<>();

    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    private SchemaAddress publisherAddress;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public @Nullable Duration getReadingTime() {
        return readingTime;
    }

    public void setReadingTime(@Nullable Duration readingTime) {
        this.readingTime = readingTime;
    }

    public SchemaAuthor getAuthor() {
        return author;
    }

    public void setAuthor(SchemaAuthor author) {
        this.author = author;
    }

    public @Nullable SchemaAuthor getEditor() {
        return editor;
    }

    public void setEditor(@Nullable SchemaAuthor editor) {
        this.editor = editor;
    }

    public Set<SchemaTag> getTags() {
        return tags;
    }

    public void setTags(Set<SchemaTag> tags) {
        this.tags = tags;
    }

    public @Nullable SchemaAddress getPublisherAddress() {
        return publisherAddress;
    }

    public void setPublisherAddress(@Nullable SchemaAddress publisherAddress) {
        this.publisherAddress = publisherAddress;
    }
}
