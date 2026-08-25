package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public class R1Book {
    @Id
    @GeneratedValue
    private String id;

    @MappedProperty("book_title")
    private String title;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private R1Author author;

    public R1Book() {}

    public R1Book(String title, R1Author author) {
        this.title = title;
        this.author = author;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public R1Author getAuthor() { return author; }
    public void setAuthor(R1Author author) { this.author = author; }
}
