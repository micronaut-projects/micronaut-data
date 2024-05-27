package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

@MappedEntity
public class NewAuthor {

    @Id
    private Long id;

    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<NewGenre> genres = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<NewGenre> getGenres() {
        return genres;
    }

    public void setGenres(List<NewGenre> genres) {
        this.genres = genres;
    }
}
