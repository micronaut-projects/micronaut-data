package io.micronaut.data.runtime.event;

import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.tck.entities.DomainEvents;

import jakarta.inject.Singleton;

@Singleton
public class TestEventBean {
    private int prePersist;

    @PrePersist
    void test(DomainEvents eventTest1) {
        prePersist++;
    }

    public int getPrePersist() {
        return prePersist;
    }
}
