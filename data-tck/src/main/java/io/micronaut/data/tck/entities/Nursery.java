package io.micronaut.data.tck.entities;


import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;


@MappedEntity
public class Nursery {
    private final String name;

    @Creator
    public Nursery(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @GeneratedValue
    @Id
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
