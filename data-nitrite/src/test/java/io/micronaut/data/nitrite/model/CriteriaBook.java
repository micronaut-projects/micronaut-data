package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public class CriteriaBook {
    @Id
    @GeneratedValue
    private String id;

    private String title;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private CriteriaAuthor author;

    public CriteriaBook() {
    }

    public CriteriaBook(String title, CriteriaAuthor author) {
        this.title = title;
        this.author = author;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public CriteriaAuthor getAuthor() {
        return author;
    }

    public void setAuthor(CriteriaAuthor author) {
        this.author = author;
    }
}
