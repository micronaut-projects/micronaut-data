package io.micronaut.data.tck.entities;

import edu.umd.cs.findbugs.annotations.Nullable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Plant {
    @Id
    @GeneratedValue
    private Long id;

    private final String name;
    @Nullable
    private final Nursery nursery;

    public Plant(String name, @Nullable Nursery nursery) {
        this.name = name;
        this.nursery = nursery;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public Nursery getNursery() {
        return nursery;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
