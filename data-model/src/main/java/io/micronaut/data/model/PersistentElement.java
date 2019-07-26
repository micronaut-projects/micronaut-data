package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.Named;

/**
 * Shared interface for a persistent element whether it be a type or a property.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface PersistentElement extends Named, AnnotationMetadataProvider {
    /**
     * The persisted name is the fully qualified name including potential schema definitions.
     *
     * @return The persisted name.
     */
    @NonNull
    String getPersistedName();
}
