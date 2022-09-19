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
import io.micronaut.data.annotation.Relation;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA's {@code ManyToMany} annotation to {@link Relation}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ManyToManyMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.ManyToMany";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<Relation> relationBuilder = AnnotationValue.builder(Relation.class);
        annotation.enumValue("cascade", Relation.Cascade.class).ifPresent(c ->
                relationBuilder.member("cascade", c)
        );
        annotation.stringValue("mappedBy").ifPresent(s -> relationBuilder.member("mappedBy", s));
        AnnotationValue<Relation> ann = relationBuilder.value(Relation.Kind.MANY_TO_MANY).build();

        return Collections.singletonList(ann);
    }
}
