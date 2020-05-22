/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Embedded;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.function.Function;

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
     * @param entityResolver The entity resolver
     */
    SourceEmbedded(SourcePersistentEntity owner, PropertyElement propertyElement, @NonNull Function<ClassElement, SourcePersistentEntity> entityResolver) {
        super(owner, propertyElement, entityResolver);
    }
}
