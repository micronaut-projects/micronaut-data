package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;

/**
 * A runtime representation of {@link Association}.
 *
 * @author graemerocher
 * @since 1.0
 */
class RuntimeAssociation extends RuntimePersistentProperty implements Association {

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The property
     */
    RuntimeAssociation(PersistentEntity owner, BeanProperty<?, ?> property) {
        super(owner, property);
    }

    @Nullable
    @Override
    public PersistentEntity getAssociatedEntity() {
        switch (getKind()) {
            case ONE_TO_MANY:
            case MANY_TO_MANY:
                Argument<?> typeArg = getProperty().asArgument().getFirstTypeVariable().orElse(null);
                if (typeArg  != null) {
                    return PersistentEntity.of(typeArg.getType());
                }
            default:
                return PersistentEntity.of(getProperty().getType());
        }
    }
}
