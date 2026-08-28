/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.processor.mappers.jakarta.data;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.OrderBy;
import io.micronaut.data.model.Sort;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps Jakarta Data @OrderBy annotation.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
public final class JakartaDataOrderByMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return "jakarta.data.repository.OrderBy";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<OrderBy> builder = AnnotationValue.builder(OrderBy.class).members(annotation.getValues());
        // Jakarta Data names the "let the database decide" constant UNSPECIFIED, Micronaut Data names it NONE
        annotation.stringValue("nullOrdering")
            .ifPresent(nullOrdering -> builder.member("nullOrdering",
                "UNSPECIFIED".equals(nullOrdering) ? Sort.Order.NullOrdering.NONE : Sort.Order.NullOrdering.valueOf(nullOrdering)));
        return List.of(builder.build());
    }
}
