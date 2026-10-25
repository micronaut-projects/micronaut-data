package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Node
public class Person {
    @Id
    @AutoPopulated
    private UUID id;
    private final String name;
    @Nullable
    private final Integer born;
    @Relationship("MENTORED_BY")
    private Person mentor;
    @Relationship(value = "KNOWS", cascade = Relation.Cascade.PERSIST)
    private List<Person> acquaintances = new ArrayList<>();
    @Relationship(value = "ENDORSED_BY", cascade = Relation.Cascade.NONE)
    private List<Person> endorsers = new ArrayList<>();

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

    public List<Person> getAcquaintances() {
        return acquaintances;
    }

    public void setAcquaintances(List<Person> acquaintances) {
        this.acquaintances = acquaintances;
    }

    public List<Person> getEndorsers() {
        return endorsers;
    }

    public void setEndorsers(List<Person> endorsers) {
        this.endorsers = endorsers;
    }
}
