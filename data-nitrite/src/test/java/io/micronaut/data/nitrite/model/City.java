package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public class City {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private State state;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
}
