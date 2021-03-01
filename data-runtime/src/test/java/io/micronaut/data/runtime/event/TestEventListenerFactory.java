package io.micronaut.data.runtime.event;

import io.micronaut.context.annotation.Factory;
import io.micronaut.data.event.listeners.*;
import io.micronaut.data.tck.entities.DomainEvents;

import javax.inject.Singleton;

@Factory
public class TestEventListenerFactory {
    private int prePersist;
    private int preUpdate;
    private int preRemove;

    private int postPersist;
    private int postUpdate;
    private int postRemove;

    @Singleton
    PrePersistEventListener<DomainEvents> prePersistEventListener() {
        return entity -> {
            prePersist++;
            return true;
        };
    }

    @Singleton
    PreUpdateEventListener<DomainEvents> preUpdateEventListener() {
        return entity -> {
            preUpdate++;
            return true;
        };
    }

    @Singleton
    PreRemoveEventListener<DomainEvents> preRemoveEventListener() {
        return entity -> {
            preRemove++;
            return true;
        };
    }

    @Singleton
    PostPersistEventListener<DomainEvents> postPersistEventListener() {
        return entity -> {
            postPersist++;
        };
    }

    @Singleton
    PostUpdateEventListener<DomainEvents> postUpdateEventListener() {
        return entity -> {
            postUpdate++;
        };
    }

    @Singleton
    PostRemoveEventListener<DomainEvents> postRemoveEventListener() {
        return entity -> {
            preRemove++;
        };
    }

    public int getPostPersist() {
        return postPersist;
    }

    public int getPostUpdate() {
        return postUpdate;
    }

    public int getPostRemove() {
        return postRemove;
    }

    public int getPreRemove() {
        return preRemove;
    }

    public int getPreUpdate() {
        return preUpdate;
    }

    public int getPrePersist() {
        return prePersist;
    }
}
