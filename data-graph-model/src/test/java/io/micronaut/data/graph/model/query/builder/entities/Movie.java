package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public class Movie {
    @Id
    private String title;
    private Integer released;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getReleased() {
        return released;
    }

    public void setReleased(Integer released) {
        this.released = released;
    }
}
