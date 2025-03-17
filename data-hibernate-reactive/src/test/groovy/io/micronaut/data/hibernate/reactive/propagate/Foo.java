package io.micronaut.data.hibernate.reactive.propagate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Foo {

    @Id
    private Long id;

    private String name;

    public Foo() {
    }

    public Foo(Long id, String name) {
        this.id = id;
        this.name = name;
    }

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
