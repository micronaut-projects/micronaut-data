/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.graph.runtime.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import org.jspecify.annotations.Nullable;

import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import java.util.Date;

/**
 * The decoder context of graph entities: values the driver returns natively, such as temporal values, are converted
 * rather than read from text, and types serde cannot deserialize fall back to conversion. Everything else is resolved
 * by the context of the serde registry.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class GraphDecoderContext implements Deserializer.DecoderContext {

    private final Deserializer.DecoderContext parent;
    private final ConversionService conversionService;

    /**
     * @param parent            The context of the serde registry
     * @param conversionService The conversion service for native values
     */
    GraphDecoderContext(Deserializer.DecoderContext parent, ConversionService conversionService) {
        this.parent = parent;
        this.conversionService = conversionService;
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        Class<? extends T> javaType = type.getType();
        if (isNative(javaType)) {
            return (Deserializer<? extends T>) new NativeValueDeserializer(conversionService, null);
        }
        try {
            return parent.findDeserializer(type);
        } catch (SerdeException e) {
            // A driver type serde has no deserializer for, such as a spatial point: pass it through
            return (Deserializer<? extends T>) new NativeValueDeserializer(conversionService, null);
        }
    }

    private static boolean isNative(Class<?> type) {
        return Temporal.class.isAssignableFrom(type) || TemporalAmount.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type);
    }

    @Override
    public <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass) throws SerdeException {
        return parent.findCustomDeserializer(deserializerClass);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return parent.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return parent.getDeserializableSubtypes(superType);
    }

    @Override
    public <B, P> @Nullable PropertyReference<B, P> resolveReference(PropertyReference<B, P> reference) {
        return parent.resolveReference(reference);
    }

    @Override
    public <B, P> void pushManagedRef(PropertyReference<B, P> reference) {
        parent.pushManagedRef(reference);
    }

    @Override
    public void popManagedRef() {
        parent.popManagedRef();
    }

    @Override
    public boolean hasView(Class<?>... views) {
        return parent.hasView(views);
    }
}
