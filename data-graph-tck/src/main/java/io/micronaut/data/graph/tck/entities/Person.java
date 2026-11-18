package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.UUID;

@Node
public class Person {
    @Id
    @AutoPopulated
    private UUID id;
    private final String name;
    private final Integer born;
    @Relationship("MENTORED_BY")
    private Person mentor;

    public Person(String name, Integer born) {
        this.name = name;
        this.born = born;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Integer getBorn() {
        return born;
    }

    public Person getMentor() {
        return mentor;
    }

    public void setMentor(Person mentor) {
        this.mentor = mentor;
    }
}
