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
package io.micronaut.data.search.tck.entities;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.vector.FloatVector;
import jakarta.persistence.Column;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A searchable entity with an embedding, for the hybrid text and vector search TCK.
 */
@MappedEntity("search_article")
@Searchable(language = "english")
public class Article {

    @Id
    @AutoPopulated
    @Nullable
    private UUID id;

    @SearchField(weight = SearchWeight.HIGH)
    private String title;

    @SearchField
    private String body;

    @Column(length = 3)
    private FloatVector embedding;

    public Article(String title, String body, FloatVector embedding) {
        this.title = title;
        this.body = body;
        this.embedding = embedding;
    }

    @Nullable
    public UUID getId() {
        return id;
    }

    public void setId(@Nullable UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public FloatVector getEmbedding() {
        return embedding;
    }

    public void setEmbedding(FloatVector embedding) {
        this.embedding = embedding;
    }

    @Override
    public String toString() {
        return "Article{" + title + '}';
    }
}
