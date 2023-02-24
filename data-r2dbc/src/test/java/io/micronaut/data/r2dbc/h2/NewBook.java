package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@MappedEntity
public class NewBook {
    @Id
    private Long id;

    @NotEmpty
    private String title;

    @Relation(value = Relation.Kind.MANY_TO_MANY,  cascade = { Relation.Cascade.PERSIST, Relation.Cascade.UPDATE })
    @Valid
    @NotNull
    private Set<NewAuthor> authors = new HashSet<>();

    public Set<NewAuthor> getAuthors() {
        return authors;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setAuthors(Set<NewAuthor> authors) {
        this.authors = authors;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    };
}
