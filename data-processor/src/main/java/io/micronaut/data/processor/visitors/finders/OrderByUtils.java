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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Sort;
import jakarta.persistence.criteria.Nulls;

/**
 * Utils for reading {@link io.micronaut.data.annotation.OrderBy} annotation values.
 *
 * @author Denis Stepanov
 * @since 5.2
 */
@Internal
public final class OrderByUtils {

    private OrderByUtils() {
    }

    /**
     * Reads the null precedence requested by an {@link io.micronaut.data.annotation.OrderBy} annotation value.
     *
     * @param orderByAnnotation The order by annotation value
     * @return The null precedence, {@link Nulls#NONE} when unspecified
     */
    public static Nulls getNullPrecedence(AnnotationValue<?> orderByAnnotation) {
        return orderByAnnotation.enumValue("nullOrdering", Sort.Order.NullOrdering.class)
            .map(nullOrdering -> switch (nullOrdering) {
                case FIRST -> Nulls.FIRST;
                case LAST -> Nulls.LAST;
                case NONE -> Nulls.NONE;
            })
            .orElse(Nulls.NONE);
    }
}
