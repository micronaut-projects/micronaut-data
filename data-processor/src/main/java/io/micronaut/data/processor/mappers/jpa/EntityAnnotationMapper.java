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
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.processor.mappers.MappedEntityMapper;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Translates the {@code javax.persistence} annotation model into the generic model understood by Micronaut Data.
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

        return Arrays.asList(
                MappedEntityMapper.buildIntrospectionConfiguration(),
                AnnotationValue.builder(MappedEntity.class).build()
        );
    }
}
