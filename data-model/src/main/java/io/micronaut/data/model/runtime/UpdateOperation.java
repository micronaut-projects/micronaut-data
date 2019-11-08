package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An update operation that updates the given entity.
 *
 * @param <E> The entity type
 * @author graemerocher
 * @since 1.0.0
 */
public interface UpdateOperation<E> extends EntityOperation<E> {

    /**
     * @return The entity to insert.
     */
    @NonNull E getEntity();

}

