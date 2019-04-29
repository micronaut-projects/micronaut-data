package io.micronaut.data.processor.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Embedded;
import io.micronaut.inject.ast.PropertyElement;

@Internal
class SourceEmbedded extends SourceAssociation implements Embedded {
    SourceEmbedded(SourcePersistentEntity owner, PropertyElement propertyElement) {
        super(owner, propertyElement);
    }
}
