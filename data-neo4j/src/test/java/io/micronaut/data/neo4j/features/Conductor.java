package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

/**
 * Sets some properties through its constructor and others through setters, so it is read by the deserializer serde
 * builds at runtime.
 */
@Node
public class Conductor {
    @Id
    private final String fullName;
    private Integer birthYear;

    public Conductor(String fullName) {
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }
}
