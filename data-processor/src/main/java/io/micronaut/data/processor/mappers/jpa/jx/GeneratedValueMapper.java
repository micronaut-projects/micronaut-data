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
package io.micronaut.data.processor.mappers.jpa.jx;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Maps JPA's generated value to Micronaut's.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class GeneratedValueMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.GeneratedValue";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<GeneratedValue> generatedValueBuilder = AnnotationValue.builder(GeneratedValue.class);
        annotation.stringValue("strategy").ifPresent(s -> {
            try {
                GeneratedValue.Type type = GeneratedValue.Type.valueOf(s);
                generatedValueBuilder.value(type);
            } catch (IllegalArgumentException e) {
                // not a compatible enum
            }
        });
        return Arrays.asList(
                generatedValueBuilder.build(),
                // include nullable for generated values, so they are excluded from null checks
                AnnotationValue.builder("javax.annotation.Nullable").build()
        );
    }

}
