package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public class R1Review {
    @Id
    @GeneratedValue
    private String id;

    private String text;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private R1Book book;

    public R1Review() {}

    public R1Review(String text, R1Book book) {
        this.text = text;
        this.book = book;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public R1Book getBook() { return book; }
    public void setBook(R1Book book) { this.book = book; }
}
