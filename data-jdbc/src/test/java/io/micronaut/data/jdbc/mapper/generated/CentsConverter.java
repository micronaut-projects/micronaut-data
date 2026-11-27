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
package io.micronaut.data.jdbc.mapper.generated;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Persists a primitive amount of cents as a decimal string, to show a converted primitive property.
 */
@Singleton
public class CentsConverter implements AttributeConverter<Long, String> {

    @Override
    @Nullable
    public String convertToPersistedValue(@Nullable Long entityValue, ConversionContext context) {
        return entityValue == null ? null : entityValue.toString();
    }

    @Override
    @Nullable
    public Long convertToEntityValue(@Nullable String persistedValue, ConversionContext context) {
        return persistedValue == null ? null : Long.valueOf(persistedValue);
    }
}
