package io.micronaut.data.document.mongodb.search;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.Language;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.mongodb.annotation.MongoSearchIndex;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The TCK {@link io.micronaut.data.search.tck.entities.Book} searched with MongoDB Search.
 */
@MappedEntity("atlas_book")
@Searchable(language = "english")
@MongoSearchIndex
public class AtlasBook {

    @Id
    @AutoPopulated
    @Nullable
    private UUID id;
    @SearchField(weight = SearchWeight.HIGH)
    private String title;
    @SearchField(weight = SearchWeight.LOW)
    private String description;
    private String author;
    private int pages;
    @Language
    @Nullable
    private String language;

    public AtlasBook(String title, String description, String author, int pages) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.pages = pages;
    }

    @Nullable
    public UUID getId() {
        return id;
    }

    public void setId(@Nullable UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }

    public void setLanguage(@Nullable String language) {
        this.language = language;
    }
}
