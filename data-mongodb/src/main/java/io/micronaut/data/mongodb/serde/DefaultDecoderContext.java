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
package io.micronaut.data.mongodb.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.AbstractPropertyReferenceManager;
import io.micronaut.serde.reference.PropertyReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Default implementation of {@link Deserializer.DecoderContext}.
 *
 */
@Internal
class DefaultDecoderContext extends AbstractPropertyReferenceManager implements Deserializer.DecoderContext {
    private final SerdeRegistry registry;
    private final @Nullable SerdeConfiguration serdeConfiguration;
    private final @Nullable DeserializationConfiguration deserializationConfiguration;

    DefaultDecoderContext(SerdeRegistry registry) {
        this(registry, null, null);
    }

    /**
     * @param registry                     The registry
     * @param serdeConfiguration           The serde configuration
     * @param deserializationConfiguration The deserialization configuration, read by generated deserializers
     */
    DefaultDecoderContext(SerdeRegistry registry,
                          @Nullable SerdeConfiguration serdeConfiguration,
                          @Nullable DeserializationConfiguration deserializationConfiguration) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.deserializationConfiguration = deserializationConfiguration;
    }

    @Override
    public Optional<SerdeConfiguration> getSerdeConfiguration() {
        return Optional.ofNullable(serdeConfiguration);
    }

    @Override
    public Optional<DeserializationConfiguration> getDeserializationConfiguration() {
        return Optional.ofNullable(deserializationConfiguration);
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
    public <B, P> PropertyReference<@NonNull B, @NonNull P> resolveReference(PropertyReference<B, P> reference) {
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
