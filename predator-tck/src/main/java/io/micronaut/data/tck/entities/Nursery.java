package io.micronaut.data.tck.entities;


import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Nursery {
    private final String name;

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
