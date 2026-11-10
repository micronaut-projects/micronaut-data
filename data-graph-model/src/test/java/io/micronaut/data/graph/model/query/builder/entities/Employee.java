package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public class Employee {
    @Id
    private Long id;
    @Version
    private Long version;
    private String name;
    @Relationship("REPORTS_TO")
    private Person manager;
    @Relationship("WORKED_ON")
    private List<Movie> movies;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Person getManager() {
        return manager;
    }

    public void setManager(Person manager) {
        this.manager = manager;
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }
}
