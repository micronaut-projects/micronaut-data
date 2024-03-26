package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

import java.time.LocalDateTime;

@Introspected
public class BookDtoWithAuthorDto {

    private String title;
    private int totalPages;
    private LocalDateTime lastUpdated;

    private AuthorDTO author;

    public BookDtoWithAuthorDto() {
    }

    public BookDtoWithAuthorDto(String title, int totalPages) {
        this.title = title;
        this.totalPages = totalPages;
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

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public AuthorDTO getAuthor() {
        return author;
    }

    public void setAuthor(AuthorDTO author) {
        this.author = author;
    }
}
