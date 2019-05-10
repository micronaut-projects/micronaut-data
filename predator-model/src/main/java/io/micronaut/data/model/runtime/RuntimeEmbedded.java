package io.micronaut.data.model.runtime;

import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;

/**
 * A runtime representation of {@link Embedded}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
class RuntimeEmbedded extends RuntimeAssociation implements Embedded {

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The bean property
     */
    RuntimeEmbedded(PersistentEntity owner, BeanProperty<?, ?> property) {
        super(owner, property);
    }
}
