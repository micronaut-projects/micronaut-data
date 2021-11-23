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
package io.micronaut.data.document.tck.entities;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

@Singleton
public class QuantityAttributeConverter implements AttributeConverter<Quantity, Integer> {

    @Override
    public Integer convertToPersistedValue(Quantity quantity, ConversionContext context) {
        return quantity == null ? null : quantity.getAmount();
    }

    @Override
    public Quantity convertToEntityValue(Integer value, ConversionContext context) {
        return value == null ? null : Quantity.valueOf(value);
    }

}
