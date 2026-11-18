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
package io.micronaut.data.jdbc.oraclexe.search;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The TCK {@link io.micronaut.data.search.tck.entities.Book} stored as one JSON column on Oracle.
 */
@MappedEntity("json_book")
@Searchable
@EntityRepresentation(type = EntityRepresentation.Type.COLUMN, columnType = EntityRepresentation.ColumnType.JSON, column = "DATA")
public class OracleJsonBook {

    @Id
    @AutoPopulated
    @Nullable
    private UUID id;
    @SearchField(weight = SearchWeight.HIGH)
    private String title;
    @SearchField(weight = SearchWeight.LOW)
    private String description;
    private String author;
    private int pages;
    @Nullable
    private String language;

    public OracleJsonBook(String title, String description, String author, int pages) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.pages = pages;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }

    public void setLanguage(@Nullable String language) {
        this.language = language;
    }
}
