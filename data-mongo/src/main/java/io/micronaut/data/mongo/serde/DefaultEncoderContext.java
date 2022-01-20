/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.mongo.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.AbstractPropertyReferenceManager;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.SerializationReference;

/**
 * Default implementation of {@link Serializer.EncoderContext}.
 *
 */
@Internal
class DefaultEncoderContext extends AbstractPropertyReferenceManager implements Serializer.EncoderContext {
    private final SerdeRegistry registry;

    DefaultEncoderContext(SerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public final <T, D extends Serializer<? extends T>> D findCustomSerializer(Class<? extends D> serializerClass)
            throws SerdeException {
        return registry.findCustomSerializer(serializerClass);
    }

    @Override
    public final <T> Serializer<? super T> findSerializer(Argument<? extends T> forType) throws SerdeException {
        return registry.findSerializer(forType);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return registry.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public <B, P> SerializationReference<B, P> resolveReference(SerializationReference<B, P> reference) {
        final Object value = reference.getReference();
        if (refs != null) {
            final PropertyReference<?, ?> managedReference = refs.peekFirst();
            if (managedReference != null && managedReference.getProperty().getName().equals(reference.getReferenceName())) {
                if (managedReference.getReference() == value) {
                    return null;
                }
            }
        }
        return reference;
    }
}
