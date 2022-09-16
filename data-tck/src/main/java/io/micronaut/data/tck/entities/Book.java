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

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private int totalPages;

    @ManyToOne(fetch = FetchType.LAZY)
    private Author author;

    @OneToOne
    private Genre genre;

    @ManyToOne
    private Publisher publisher;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "book")
    private List<Page> pages = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "book")
    private List<Chapter> chapters = new ArrayList<>();

    @ManyToMany(cascade = CascadeType.PERSIST)
    private Set<Student> students = new HashSet<>();

    @Transient
    public int prePersist, postPersist, preUpdate, postUpdate, preRemove, postRemove, postLoad;

    @DateUpdated
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onPrePersist() {
        prePersist++;
    }
// Hibernate doesn't support multiple pre persist
//    @PrePersist
//    protected void onPrePersist2() {
//    }

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
