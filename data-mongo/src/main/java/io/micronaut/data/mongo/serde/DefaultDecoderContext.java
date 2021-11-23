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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.AbstractPropertyReferenceManager;
import io.micronaut.serde.reference.PropertyReference;

import java.util.Collection;

/**
 * Default implementation of {@link Deserializer.DecoderContext}.
 *
 */
@Internal
class DefaultDecoderContext extends AbstractPropertyReferenceManager implements Deserializer.DecoderContext {
    private final SerdeRegistry registry;

    DefaultDecoderContext(SerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public final <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass)
            throws SerdeException {
        return registry.findCustomDeserializer(deserializerClass);
    }

    @Override
    public final <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        return registry.findDeserializer(type);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return registry.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public final <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return registry.getDeserializableSubtypes(superType);
    }

    @Override
    public <B, P> PropertyReference<B, P> resolveReference(PropertyReference<B, P> reference) {
        if (refs != null) {
            final PropertyReference<?, ?> first = refs.peekFirst();
            if (first != null) {
                if (first.getReferenceName().equals(reference.getProperty().getName())) {
                    final Object o = first.getReference();
                    if (o != null) {
                        //noinspection unchecked
                        return (PropertyReference<B, P>) first;
                    }
                }
            }
        }
        return reference;
    }
}
