package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.Named;

/**
 * An operation on an entity type.
 * @param <E> The entity type
 */
public interface EntityOperation<E> extends Named, AnnotationMetadataProvider {
    /**
     * The root entity type.
     *
     * @return The root entity type
     */
    @NonNull
    Class<E> getRootEntity();
}
