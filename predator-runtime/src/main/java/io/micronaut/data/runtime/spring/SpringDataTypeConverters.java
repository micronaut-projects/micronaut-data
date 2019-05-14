/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.spring;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.TypeConverter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Type converters for supporting classes and interfaces in the {@link org.springframework.data.domain} package.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Factory
@Internal
public class SpringDataTypeConverters {

    /**
     * @return The page converter
     */
    @Singleton
    TypeConverter<io.micronaut.data.model.Page, Page> pageConverter() {
        return (object, targetType, context) -> Optional.of(new PageDelegate(object));
    }

    /**
     * @return The pageable converter
     */
    @Singleton
    TypeConverter<Pageable, io.micronaut.data.model.Pageable> pageableConverter() {
        return (object, targetType, context) -> Optional.of(new PageableDelegate(object));
    }

    /**
     * @return The sort converter
     */
    @Singleton
    TypeConverter<Sort, io.micronaut.data.model.query.Sort> sortConverter() {
        return (object, targetType, context) -> Optional.of(new SortDelegate(object));
    }
}
