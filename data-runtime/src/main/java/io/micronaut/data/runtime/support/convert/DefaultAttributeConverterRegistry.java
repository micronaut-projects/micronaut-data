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
package io.micronaut.data.runtime.support.convert;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Default implementation of {@link AttributeConverterRegistry}.
 */
@Singleton
@Internal
final class DefaultAttributeConverterRegistry implements AttributeConverterRegistry {

    private final BeanLocator beanLocator;
    private final List<AttributeConverterProvider> attributeConverterTransformers;

    DefaultAttributeConverterRegistry(BeanLocator beanLocator, List<AttributeConverterProvider> attributeConverterTransformers) {
        this.beanLocator = beanLocator;
        this.attributeConverterTransformers = attributeConverterTransformers;
    }

    @Override
    public AttributeConverter<Object, Object> getConverter(Class<?> converterClass) {
        if (AttributeConverter.class.isAssignableFrom(converterClass)) {
            return (AttributeConverter<Object, Object>) beanLocator.getBean(converterClass);
        }
        for (AttributeConverterProvider transformer : attributeConverterTransformers) {
            if (transformer.supports(converterClass)) {
                return transformer.provide(beanLocator, converterClass);
            }
        }
        throw new IllegalStateException("Unknown converter type: " + converterClass);
    }
}
