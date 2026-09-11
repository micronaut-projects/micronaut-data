package io.micronaut.data.nitrite.runtime;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * The generic subject of {@code DefaultNitriteRepositoryOperationsSpec}: an identity, a string
 * property, and a {@code char} property, which is the only primitive the mapper narrows on read.
 */
@MappedEntity
public class OperationsEntity {
    @Id
    private Long id;
    private String name;
    private char initial;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public char getInitial() { return initial; }
    public void setInitial(char initial) { this.initial = initial; }
}
