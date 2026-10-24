package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node
public class Colleague {
    @Id
    private final String name;
    @Relationship(type = "WORKS_WITH", direction = Direction.UNDIRECTED)
    private List<Collaboration> colleagues = new ArrayList<>();

    public Colleague(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<Collaboration> getColleagues() {
        return colleagues;
    }

    public void setColleagues(List<Collaboration> colleagues) {
        this.colleagues = colleagues;
    }
}
