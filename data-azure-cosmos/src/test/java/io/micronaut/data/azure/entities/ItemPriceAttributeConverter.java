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
package io.micronaut.data.azure.entities;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

@Singleton
public class ItemPriceAttributeConverter implements AttributeConverter<ItemPrice, Double> {

    @Override
    public Double convertToPersistedValue(ItemPrice bookPrice, ConversionContext context) {
        return bookPrice == null ? null : bookPrice.getPrice();
    }

    @Override
    public ItemPrice convertToEntityValue(Double value, ConversionContext context) {
        return value == null ? null : ItemPrice.valueOf(value);
    }

}
