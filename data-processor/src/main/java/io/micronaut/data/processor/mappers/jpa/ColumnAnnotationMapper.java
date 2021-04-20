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
package io.micronaut.data.processor.mappers.jpa;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps JPA column annotation to {@link MappedProperty}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class ColumnAnnotationMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Column";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final String name = annotation.stringValue("name").orElse(null);
        final boolean nullable = annotation.booleanValue("nullable").orElse(false);
        AnnotationValue<MappedProperty> persistedAnnotationValue;
        List<AnnotationValue<?>> values = new ArrayList<>(2);
        if (name != null) {
            AnnotationValueBuilder<MappedProperty> builder = AnnotationValue.builder(MappedProperty.class)
                                                                            .value(name);
            annotation.stringValue("columnDefinition").ifPresent(s -> builder.member("definition", s));
            persistedAnnotationValue = builder
                    .build();

            values.add(persistedAnnotationValue);
        } else {
            annotation.stringValue("columnDefinition").ifPresent(s ->
                    values.add(AnnotationValue.builder(MappedProperty.class)
                                              .member("definition", s).build()));
        }

        if (nullable) {
            values.add(AnnotationValue.builder("javax.annotation.Nullable").build());
        }

        return values;
    }

}
