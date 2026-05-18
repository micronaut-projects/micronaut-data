/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.spring.jpa.intercept;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification;
import org.jspecify.annotations.NullUnmarked;

/**
 * The Spring to Micronaut Data specification converters.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Requires(classes = org.springframework.data.jpa.domain.Specification.class)
@Prototype
@Internal
final class SpecificationConverters implements TypeConverterRegistrar {
    @Override
    @NullUnmarked
    public void register(MutableConversionService conversionService) {
        conversionService.addConverter(
            org.springframework.data.jpa.domain.Specification.class,
            QuerySpecification.class,
            specification -> specification::toPredicate
        );
        conversionService.addConverter(
            org.springframework.data.jpa.domain.Specification.class,
            DeleteSpecification.class,
            specification -> (root, query, criteriaBuilder) -> specification.toPredicate(root, null, criteriaBuilder)
        );
        conversionService.addConverter(
            org.springframework.data.jpa.domain.Specification.class,
            UpdateSpecification.class,
            specification -> (root, query, criteriaBuilder) -> specification.toPredicate(root, null, criteriaBuilder)
        );
        conversionService.addConverter(
            org.springframework.data.jpa.domain.UpdateSpecification.class,
            UpdateSpecification.class,
            specification -> specification::toPredicate
        );
        conversionService.addConverter(
            org.springframework.data.jpa.domain.DeleteSpecification.class,
            DeleteSpecification.class,
            specification -> specification::toPredicate
        );
        conversionService.addConverter(org.springframework.data.domain.Sort.class, Sort.class, springSort -> Sort.of(
            springSort.get().map(sort -> new Sort.Order(
                    sort.getProperty(),
                    sort.isAscending() ? Sort.Order.Direction.ASC : Sort.Order.Direction.DESC,
                    sort.isIgnoreCase()
                )
            ).toList())
        );
    }
}
