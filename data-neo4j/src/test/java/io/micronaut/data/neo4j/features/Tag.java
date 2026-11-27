package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public class Tag {
    @Id
    @GeneratedValue
    private Long id;
    private final String name;

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
}
