/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.processor.mappers.support;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

/**
 * Maps {@link Nullable} to javax nullable.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class NullableAnnotationMapper implements TypedAnnotationMapper<Nullable> {
    @Override
    public Class<Nullable> annotationType() {
        return Nullable.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Nullable> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder("javax.annotation.Nullable").build()
        );
    }
}
