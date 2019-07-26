package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class AuthorDTO {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
