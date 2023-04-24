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
package io.micronaut.data.document.serde.defaults;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.document.serde.CustomConverterDeserializer;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableDeserializer;
import jakarta.inject.Singleton;

/**
 * Default deserializer for custom converters.
 *
 * @author radovanradic
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Singleton
final class DefaultCustomConverterDeserializer implements CustomConverterDeserializer, CustomizableDeserializer<Object> {

    private final AttributeConverterRegistry attributeConverterRegistry;

    public DefaultCustomConverterDeserializer(AttributeConverterRegistry attributeConverterRegistry) {
        this.attributeConverterRegistry = attributeConverterRegistry;
    }

    @Override
    @NonNull
    public Deserializer<Object> createSpecific(DecoderContext decoderContext, Argument<? super Object> type) throws SerdeException {
        Class<?> converterClass = type.getAnnotationMetadata().classValue(MappedProperty.class, "converter")
            .orElseThrow(IllegalStateException::new);
        Class<Object> converterPersistedType = type.getAnnotationMetadata().classValue(MappedProperty.class, "converterPersistedType")
            .orElseThrow(IllegalStateException::new);
        Argument<Object> convertedType = Argument.of(converterPersistedType);
        AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
        Deserializer<?> deserializer = decoderContext.findDeserializer(convertedType);
        return (decoder, decoderContext1, type1) -> {
            if (decoder.decodeNull()) {
                return null;
            }
            Object deserialized = deserializer.deserialize(decoder, decoderContext1, convertedType);
            return converter.convertToEntityValue(deserialized, ConversionContext.of(convertedType));
        };
    }

}
