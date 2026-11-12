package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.time.Instant;

@Node
public class Employee {
    @Id
    private final String name;
    @DateCreated
    private Instant created;
    @Relationship("REPORTS_TO")
    private Employee manager;

    public Employee(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Employee getManager() {
        return manager;
    }

    public void setManager(Employee manager) {
        this.manager = manager;
    }
}
