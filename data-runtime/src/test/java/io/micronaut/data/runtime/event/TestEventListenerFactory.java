package io.micronaut.data.runtime.event;

import io.micronaut.context.annotation.Factory;
import io.micronaut.data.annotation.event.PrePersist;
import io.micronaut.data.event.listeners.*;

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
    PrePersistEventListener<EventTest1> prePersistEventListener() {
        return entity -> {
            prePersist++;
            return true;
        };
    }

    @Singleton
    PreUpdateEventListener<EventTest1> preUpdateEventListener() {
        return entity -> {
            preUpdate++;
            return true;
        };
    }

    @Singleton
    PreRemoveEventListener<EventTest1> preRemoveEventListener() {
        return entity -> {
            preRemove++;
            return true;
        };
    }

    @Singleton
    PostPersistEventListener<EventTest1> postPersistEventListener() {
        return entity -> {
            postPersist++;
        };
    }

    @Singleton
    PostUpdateEventListener<EventTest1> postUpdateEventListener() {
        return entity -> {
            postUpdate++;
        };
    }

    @Singleton
    PostRemoveEventListener<EventTest1> postRemoveEventListener() {
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
