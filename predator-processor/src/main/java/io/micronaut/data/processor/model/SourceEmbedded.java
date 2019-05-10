package io.micronaut.data.processor.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.inject.ast.PropertyElement;

/**
 * Source code level implementation of {@link Embedded}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class SourceEmbedded extends SourceAssociation implements Embedded {
    /**
     * Default constructor.
     * @param owner The owner
     * @param propertyElement The property element
     */
    SourceEmbedded(SourcePersistentEntity owner, PropertyElement propertyElement) {
        super(owner, propertyElement);
    }
}
