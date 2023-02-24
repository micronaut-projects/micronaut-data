package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@MappedEntity
public class NewAuthor {

    @Id
    private Long id;

    @NotEmpty
    private String name;

    @NotNull
    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "authors")
    private Set<NewBook> books = new HashSet<>();

    public Set<NewBook> getBooks() {
        return books;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setBooks(Set<NewBook> books) {
        this.books = books;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}
