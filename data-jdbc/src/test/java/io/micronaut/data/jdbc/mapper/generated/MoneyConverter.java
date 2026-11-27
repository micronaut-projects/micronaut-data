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
 * Persists {@link Money} as its cents, and reads a SQL NULL as a zero amount to show that the converter sees the
 * null, as it does with the general mapper.
 */
@Singleton
public class MoneyConverter implements AttributeConverter<Money, Long> {

    @Override
    @Nullable
    public Long convertToPersistedValue(@Nullable Money entityValue, ConversionContext context) {
        return entityValue == null ? null : entityValue.cents();
    }

    @Override
    public Money convertToEntityValue(@Nullable Long persistedValue, ConversionContext context) {
        return new Money(persistedValue == null ? 0L : persistedValue);
    }
}
