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
package io.micronaut.data.processor.mappers.jpa;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Translates the {@code javax.persistence} annotation model into the generic model understood by Predator.
 *
 * @author graemerocher
 * @since 1.0
 */
public final class EntityAnnotationMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Entity";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<Introspected> builder = AnnotationValue.builder(Introspected.class)
                // don't bother with transients properties
                .member("excludedAnnotations", Transient.class)
                // following are indexed for fast lookups
                .member("indexed",
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", Id.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", Version.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", DateCreated.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", DateUpdated.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", Persisted.class)
                                .member("member", "value").build()
                );
        return Arrays.asList(
                builder.build(),
                AnnotationValue.builder(Persisted.class).build()
        );
    }
}
