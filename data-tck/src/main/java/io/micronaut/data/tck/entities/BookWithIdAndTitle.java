package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class BookWithIdAndTitle {

    private Long id;
    private String title;

    public BookWithIdAndTitle() {
    }

    public BookWithIdAndTitle(Long id, String title) {
        this.id = id;
        this.title = title;
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
}
