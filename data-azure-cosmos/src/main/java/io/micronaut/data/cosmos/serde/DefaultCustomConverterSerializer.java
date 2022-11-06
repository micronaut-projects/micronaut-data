/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.cosmos.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.document.serde.CustomConverterSerializer;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

/**
 * Default deserializer for custom converters.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Singleton
final class DefaultCustomConverterSerializer implements CustomConverterSerializer {

    private final AttributeConverterRegistry attributeConverterRegistry;

    public DefaultCustomConverterSerializer(AttributeConverterRegistry attributeConverterRegistry) {
        this.attributeConverterRegistry = attributeConverterRegistry;
    }

    @Override
    @NonNull
    public Serializer<Object> createSpecific(EncoderContext encoderContext, Argument<?> type) throws SerdeException {
        Class<?> converterClass = type.getAnnotationMetadata().classValue(MappedProperty.class, "converter")
            .orElseThrow(IllegalStateException::new);
        Class<Object> converterPersistedType = type.getAnnotationMetadata().classValue(MappedProperty.class, "converterPersistedType")
            .orElseThrow(IllegalStateException::new);
        Argument<Object> convertedType = Argument.of(converterPersistedType);
        Serializer<? super Object> serializer = encoderContext.findSerializer(convertedType);
        AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
        return (encoder, context, type1, value) -> {
            Object converted = converter.convertToPersistedValue(value, ConversionContext.of(type1));
            if (converted == null) {
                encoder.encodeNull();
                return;
            }
            serializer.serialize(encoder, context, convertedType, converted);
        };
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<?> type, @NonNull Object value) {
        throw new IllegalStateException("Create specific call is required!");
    }

}
