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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Projection;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps Jakarta Data @Select.List annotation.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class JakartaDataSelectListMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return "jakarta.data.repository.Select.List";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        JakartaDataSelectListMapper jakartaDataOrderByMapper = new JakartaDataSelectListMapper();
        return List.of(
            AnnotationValue.builder(Projection.List.class).values(
                annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER)
                    .stream()
                    .flatMap(av -> jakartaDataOrderByMapper.map(av, visitorContext).stream())
                    .toArray(AnnotationValue[]::new)
            ).build()
        );
    }
}
