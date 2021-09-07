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
import io.micronaut.data.model.runtime.convert.TypeConverter;
import io.micronaut.data.runtime.support.convert.TypeConverterProvider;
import jakarta.inject.Singleton;

import javax.persistence.AttributeConverter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Javax persistence {@link javax.persistence.AttributeConverter} converter provider.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
@Requires(classes = AttributeConverter.class)
@Singleton
final class JxTypeConverterProvider implements TypeConverterProvider {

    private final Map<Class, TypeConverter<Object, Object>> providersCache = new ConcurrentHashMap<>();

    @Override
    public TypeConverter<Object, Object> provide(BeanLocator beanLocator, Class<?> converterType) {
        return providersCache.computeIfAbsent(converterType, c -> {
            AttributeConverter<Object, Object> attributeConverter = (AttributeConverter<Object, Object>)
                    beanLocator.findBean(converterType).orElseThrow(() -> new IllegalStateException("Cannot find a converter bean: " + converterType.getName() + " make sure it's annotated with @Converter"));
            return new JxTypeConverter(attributeConverter);
        });
    }

    @Override
    public boolean supports(Class<?> converterType) {
        return AttributeConverter.class.isAssignableFrom(converterType);
    }

    private static final class JxTypeConverter implements TypeConverter<Object, Object> {

        private final AttributeConverter<Object, Object> converter;

        private JxTypeConverter(AttributeConverter<Object, Object> converter) {
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
