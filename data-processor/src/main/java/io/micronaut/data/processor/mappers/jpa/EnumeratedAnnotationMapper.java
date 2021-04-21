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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA's @Enumerated to a correct type def.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
public final class EnumeratedAnnotationMapper implements NamedAnnotationMapper {
    @Nonnull
    @Override
    public String getName() {
        return "javax.persistence.Enumerated";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        String enumMapping = annotation.stringValue().orElse("ORDINAL");
        if ("STRING".equals(enumMapping)) {
            return Collections.singletonList(
                    AnnotationValue.builder(TypeDef.class)
                            .member("type", DataType.STRING)
                            .build()
            );
        }
        return Collections.singletonList(
                AnnotationValue.builder(TypeDef.class)
                        .member("type", DataType.INTEGER)
                        .build()
        );
    }
}
