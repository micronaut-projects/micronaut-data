package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity
public class R1Author {
    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "author")
    private List<R1Book> books;

    public R1Author() {}

    public R1Author(String name) {
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<R1Book> getBooks() { return books; }
    public void setBooks(List<R1Book> books) { this.books = books; }
}
