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
package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.DateUpdated;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@javax.persistence.Entity
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.IDENTITY)
    private Long id;
    private String title;
    private int totalPages;

    @ManyToOne(fetch = FetchType.LAZY)
    @javax.persistence.ManyToOne(fetch = javax.persistence.FetchType.LAZY)
    private Author author;

    @OneToOne
    @javax.persistence.OneToOne
    private Genre genre;

    @ManyToOne
    @javax.persistence.ManyToOne
    private Publisher publisher;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "book")
    @javax.persistence.OneToMany(cascade = javax.persistence.CascadeType.ALL, mappedBy = "book")
    private List<Page> pages = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "book")
    @javax.persistence.OneToMany(cascade = javax.persistence.CascadeType.ALL, mappedBy = "book")
    private List<Chapter> chapters = new ArrayList<>();

    @ManyToMany(cascade = CascadeType.PERSIST)
    @javax.persistence.ManyToMany(cascade = javax.persistence.CascadeType.PERSIST)
    private Set<Student> students = new HashSet<>();

    @Transient
    @javax.persistence.Transient
    public int prePersist, postPersist, preUpdate, postUpdate, preRemove, postRemove, postLoad;

    @DateUpdated
    private LocalDateTime lastUpdated;

    @PrePersist
    @javax.persistence.PrePersist
    protected void onPrePersist() {
        prePersist++;
    }
// Hibernate doesn't support multiple pre persist
//    @PrePersist
//    protected void onPrePersist2() {
//    }

    @PostPersist
    @javax.persistence.PostPersist
    protected void onPostPersist() {
        postPersist++;
    }

    @PreUpdate
    @javax.persistence.PreUpdate
    protected void onPreUpdate() {
        preUpdate++;
    }

    @PostUpdate
    @javax.persistence.PostUpdate
    protected void onPostUpdate() {
        postUpdate++;
    }

    @PreRemove
    @javax.persistence.PreRemove
    protected void onPreRemove() {
        preRemove++;
    }

    @PostRemove
    @javax.persistence.PostRemove
    protected void onPostRemove() {
        postRemove++;
    }

    @PostLoad
    @javax.persistence.PostLoad
    protected void onPostLoad() {
        postLoad++;
    }

    @Transient
    @javax.persistence.Transient
    public void resetEventCounters() {
        prePersist = 0;
        postPersist = 0;
        preUpdate = 0;
        postUpdate = 0;
        preRemove = 0;
        postRemove = 0;
        postLoad = 0;
    }


    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public Genre getGenre() {
        return genre;
    }

    public void setGenre(Genre genre) {
        this.genre = genre;
    }

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

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<Chapter> getChapters() { return chapters; }

    public void setChapters(List<Chapter> chapters) { this.chapters = chapters; }

    public Set<Student> getStudents() {
        return students;
    }

    public void setStudents(Set<Student> students) {
        this.students = students;
    }
}
