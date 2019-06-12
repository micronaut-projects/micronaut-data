package io.micronaut.data.jdbc;

import javax.persistence.*;

@Entity
public class Book {
    @Id
    @GeneratedValue
    private Long id;
    private String title;
    private int pages;

//    @ManyToOne(fetch = FetchType.LAZY)
//    private Author author;
//
//    public Author getAuthor() {
//        return author;
//    }
//
//    public void setAuthor(Author author) {
//        this.author = author;
//    }

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

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}

