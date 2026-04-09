/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.micronaut.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "book")
public class Book {

    @Id
    private Long id;
    private String title;
    private int pages;

    @ManyToOne(cascade = CascadeType.ALL)
    private Category category;

    public Book() {
    }

    public Book(Long id, String title, int pages, Category category) {
        this.id = id;
        this.title = title;
        this.pages = pages;
        this.category = category;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Long getId() {
        return id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setId(Long id) {
        this.id = id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public String getTitle() {
        return title;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setTitle(String title) {
        this.title = title;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public int getPages() {
        return pages;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setPages(int pages) {
        this.pages = pages;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Category getCategory() {
        return category;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setCategory(Category category) {
        this.category = category;
    }
}
