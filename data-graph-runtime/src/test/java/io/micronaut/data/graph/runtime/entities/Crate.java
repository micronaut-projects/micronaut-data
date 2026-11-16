package io.micronaut.data.graph.runtime.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Sets its relationships through setters, and starts with the value the class initializes.
 */
@Node
@Serdeable
public class Crate {
    @Id
    private String name;
    @Relationship("HOLDS")
    private List<Item> items;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }
}
