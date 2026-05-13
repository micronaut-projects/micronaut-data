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

package io.micronaut.data.tck.entities;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;

@Entity
public class ClientCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Book> books = new ArrayList<>();
    private byte[] bytes = {};

    public ClientCategory() {
    }

    public ClientCategory(String name, List<Book> books, byte[] bytes) {
        this.name = name;
        this.books = books;
        this.bytes = bytes;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public long getId() {
        return id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setId(long id) {
        this.id = id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public String getName() {
        return name;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setName(String name) {
        this.name = name;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public List<Book> getBooks() {
        return books;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setBooks(List<Book> books) {
        this.books = books;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public byte[] getBytes() {
        return bytes;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

}
