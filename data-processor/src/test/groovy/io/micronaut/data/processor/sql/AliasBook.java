package io.micronaut.data.processor.sql;

import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.MappedProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;

@Entity
public class AliasBook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private int totalPages;

    @MappedProperty(alias = "au")
    @ManyToOne(fetch = FetchType.LAZY)
    private AliasAuthor author;

    @ManyToOne(fetch = FetchType.LAZY)
    private AliasAuthor coAuthor;

    @DateUpdated
    private LocalDateTime lastUpdated;

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

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public AliasAuthor getAuthor() {
        return author;
    }

    public void setAuthor(AliasAuthor author) {
        this.author = author;
    }

    public AliasAuthor getCoAuthor() {
        return coAuthor;
    }

    public void setCoAuthor(AliasAuthor coAuthor) {
        this.coAuthor = coAuthor;
    }
}
