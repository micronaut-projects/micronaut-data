package io.micronaut.data.jakarta.tck;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// Prevent Hibernate failing for tests without entities
@Entity
public class DummyBean {

    @Id
    private Long id;
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
