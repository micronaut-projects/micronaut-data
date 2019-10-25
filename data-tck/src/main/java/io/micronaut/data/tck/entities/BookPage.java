package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class BookPage {
    private Book book;
    private Page page;

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }
}
