package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Introspected;

/**
 * Name-only projection of {@link OperationsEntity}, used by the repository operation tests.
 */
@Introspected
public class NameProjection {
    private String name;

    public NameProjection() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
