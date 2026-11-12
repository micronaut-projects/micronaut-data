package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node
public class Movie {
    @Id
    private final String title;
    @MappedProperty("tagline")
    private final String description;
    private final Integer released;
    private Long votes;
    @Relationship(type = "ACTED_IN", direction = Direction.INCOMING)
    private List<Role> roles = new ArrayList<>();
    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    private List<Person> directors = new ArrayList<>();

    public Movie(String title, String description, Integer released) {
        this.title = title;
        this.description = description;
        this.released = released;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getReleased() {
        return released;
    }

    public Long getVotes() {
        return votes;
    }

    public void setVotes(Long votes) {
        this.votes = votes;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    public List<Person> getDirectors() {
        return directors;
    }

    public void setDirectors(List<Person> directors) {
        this.directors = directors;
    }
}
