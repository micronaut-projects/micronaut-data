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
package io.micronaut.data.jdbc.mapper;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import org.jspecify.annotations.Nullable;

/**
 * What a {@link GeneratedRowMapper} needs beyond the result set: the conversion service, for the values a getter
 * does not return in the property type, and the attribute converter of every column that has one, with the
 * conversion context of its property, both resolved once per plan by
 * {@link FlatEntityMappingPlan#createContext}.
 *
 * @since 5.3.0
 */
@Internal
public final class GeneratedRowMapperContext {

    private final ConversionService conversionService;
    private final AttributeConverter<Object, Object>[] converters;
    private final ConversionContext[] conversionContexts;

    /**
     * @param conversionService  The conversion service
     * @param converters         The attribute converter of every column of the mapper, {@code null} where there is none
     * @param conversionContexts The conversion context of every converted column, {@code null} where there is no converter
     */
    public GeneratedRowMapperContext(ConversionService conversionService,
                                     AttributeConverter<Object, Object>[] converters,
                                     ConversionContext[] conversionContexts) {
        this.conversionService = conversionService;
        this.converters = converters;
        this.conversionContexts = conversionContexts;
    }

    /**
     * @return The conversion service
     */
    public ConversionService conversionService() {
        return conversionService;
    }

    /**
     * Applies the attribute converter of a column to the value read, as the general mapper does, {@code null}
     * included.
     *
     * @param column         The index of the column in {@link GeneratedRowMapper#columnNames()}
     * @param persistedValue The value read with the typed getter of the persisted type
     * @return The entity value
     */
    @Nullable
    public Object convertToEntityValue(int column, @Nullable Object persistedValue) {
        AttributeConverter<Object, Object> converter = converters[column];
        ConversionContext context = conversionContexts[column];
        if (converter == null || context == null) {
            throw new IllegalStateException("No attribute converter was resolved for column " + column);
        }
        return converter.convertToEntityValue(persistedValue, context);
    }
}
