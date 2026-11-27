package io.micronaut.data.neo4j.features;

import io.micronaut.context.annotation.Factory;
import io.micronaut.data.event.listeners.PrePersistEventListener;
import io.micronaut.data.event.listeners.PreUpdateEventListener;
import jakarta.inject.Singleton;

/**
 * Vetoes the writes of the drafts named "veto".
 */
@Factory
public class DraftListener {

    @Singleton
    PrePersistEventListener<Draft> vetoPersist() {
        return draft -> !"veto".equals(draft.getName());
    }

    @Singleton
    PreUpdateEventListener<Draft> vetoUpdate() {
        return draft -> !"veto".equals(draft.getName());
    }
}
