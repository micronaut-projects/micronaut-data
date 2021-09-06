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
import io.micronaut.data.model.runtime.convert.TypeConverter;

/**
 * Intended to support different implementation of type converters like JPA' jakarta.persistence.AttributeConverter.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public interface TypeConverterProvider {

    /**
     * Provide an instance of passed converter type class.
     *
     * @param beanLocator   The bean locator
     * @param converterType The converter type
     * @return the type converter represented by this converter type
     */
    TypeConverter<Object, Object> provide(BeanLocator beanLocator, Class<?> converterType);

    /**
     * Does support providing an instance of this converter class.
     *
     * @param converterType The converter class.
     * @return true if supports
     */
    boolean supports(Class<?> converterType);

}
