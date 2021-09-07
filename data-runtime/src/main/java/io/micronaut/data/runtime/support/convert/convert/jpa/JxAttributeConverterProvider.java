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
package io.micronaut.data.runtime.support.convert.convert.jpa;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.support.convert.AttributeConverterProvider;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Javax persistence {@link javax.persistence.AttributeConverter} converter provider.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
@Requires(classes = javax.persistence.AttributeConverter.class)
@Singleton
final class JxAttributeConverterProvider implements AttributeConverterProvider {

    private final Map<Class, AttributeConverter<Object, Object>> providersCache = new ConcurrentHashMap<>();

    @Override
    public AttributeConverter<Object, Object> provide(BeanLocator beanLocator, Class<?> converterType) {
        return providersCache.computeIfAbsent(converterType, c -> {
            javax.persistence.AttributeConverter attributeConverter = (javax.persistence.AttributeConverter)
                    beanLocator.findBean(converterType).orElseThrow(() -> new IllegalStateException("Cannot find a converter bean: " + converterType.getName() + " make sure it's annotated with @Converter"));
            return new JxAttributeConverter(attributeConverter);
        });
    }

    @Override
    public boolean supports(Class<?> converterType) {
        return javax.persistence.AttributeConverter.class.isAssignableFrom(converterType);
    }

    private static final class JxAttributeConverter implements AttributeConverter<Object, Object> {

        private final javax.persistence.AttributeConverter converter;

        private JxAttributeConverter(javax.persistence.AttributeConverter converter) {
            this.converter = converter;
        }

        @Override
        public Object convertToPersistedValue(Object entityValue, ConversionContext context) {
            return converter.convertToDatabaseColumn(entityValue);
        }

        @Override
        public Object convertToEntityValue(Object persistedValue, ConversionContext context) {
            return converter.convertToEntityAttribute(persistedValue);
        }
    }

}
