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
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA's {@code Transient} annotation to {@link io.micronaut.data.annotation.Transient}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class TransientAnnotationMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Transient";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(io.micronaut.data.annotation.Transient.class)
                .build());
    }
}
