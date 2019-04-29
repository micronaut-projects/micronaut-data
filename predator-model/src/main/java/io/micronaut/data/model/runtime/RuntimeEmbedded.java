package io.micronaut.data.model.runtime;

import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;

class RuntimeEmbedded extends RuntimeAssociation implements Embedded {

    RuntimeEmbedded(PersistentEntity owner, BeanProperty<?, ?> property) {
        super(owner, property);
    }
}
