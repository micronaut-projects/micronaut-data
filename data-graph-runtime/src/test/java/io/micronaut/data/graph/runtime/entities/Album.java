package io.micronaut.data.graph.runtime.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Sets some properties through its constructor and others through setters, and this module generates no serdes, so it
 * is read by the deserializer serde builds at runtime.
 */
@Node
@Serdeable
public class Album {
    @Id
    private final String title;
    private LocalDate released;
    private Instant recorded;

    public Album(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getReleased() {
        return released;
    }

    public void setReleased(LocalDate released) {
        this.released = released;
    }

    public Instant getRecorded() {
        return recorded;
    }

    public void setRecorded(Instant recorded) {
        this.recorded = recorded;
    }
}
