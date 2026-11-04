package io.micronaut.data.runtime.event;

import io.micronaut.data.annotation.event.PostLoad;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.tck.entities.DomainEvents;

import jakarta.inject.Singleton;

@Singleton
public class TestEventBean {
    private int prePersist;
    private int postLoad;

    @PrePersist
    void test(DomainEvents eventTest1) {
        prePersist++;
    }

    @PostLoad
    void postLoad(DomainEvents eventTest1) {
        postLoad++;
    }

    public int getPrePersist() {
        return prePersist;
    }

    public int getPostLoad() {
        return postLoad;
    }
}
