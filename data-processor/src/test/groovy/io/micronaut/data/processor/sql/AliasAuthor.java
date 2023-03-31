/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.processor.sql;

import io.micronaut.data.annotation.MappedProperty;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
public class AliasAuthor {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    @Column(nullable = true)
    private String nickName;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    private Set<AliasBook> books = new HashSet<>();

    @MappedProperty(alias = "ob")
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    private Set<AliasBook> otherBooks = new HashSet<>();

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

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public Set<AliasBook> getBooks() {
        return books;
    }

    public void setBooks(Set<AliasBook> books) {
        this.books = books;
    }

    @Override
    public String toString() {
        return "Author{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", nickName='" + nickName + '\'' +
            ", books=" + books +
            '}';
    }

    public Set<AliasBook> getOtherBooks() {
        return otherBooks;
    }

    public void setOtherBooks(Set<AliasBook> otherBooks) {
        this.otherBooks = otherBooks;
    }
}
