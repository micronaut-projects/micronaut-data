package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public class Draft {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    public Draft(String name) {
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

    public void setName(String name) {
        this.name = name;
    }
}
