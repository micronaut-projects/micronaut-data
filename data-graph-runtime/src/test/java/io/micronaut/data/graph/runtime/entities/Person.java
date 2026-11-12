package io.micronaut.data.graph.runtime.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public class Person {
    @Id
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
