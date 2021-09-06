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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.runtime.convert.TypeConverter;

/**
 * Type converter registry.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
public interface TypeConverterRegistry {

    /**
     * Returns the converter instance.
     *
     * NOTE: The converter class might not implement {@link TypeConverter} when supporting external converters.
     *
     * @param converterClass The converter class.
     * @return new instance of type converter.
     */
    @NonNull
    TypeConverter<Object, Object> getConverter(@NonNull Class<?> converterClass);

}
