package io.micronaut.data.runtime.event;

import io.micronaut.data.annotation.event.PrePersist;

import javax.inject.Singleton;

@Singleton
public class TestEventBean {
    private int prePersist;

    @PrePersist
    void test(EventTest1 eventTest1) {
        prePersist++;
    }

    public int getPrePersist() {
        return prePersist;
    }
}
