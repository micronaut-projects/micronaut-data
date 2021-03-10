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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA's {@code JoinColumn} annotation to Micronaut's.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
public final class JoinColumnAnnotationMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.JoinColumn";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder("io.micronaut.data.jdbc.annotation.JoinColumn");
        annotation.stringValue("name").ifPresent(name -> builder.member("name", name));
        annotation.stringValue("referencedColumnName").ifPresent(name -> builder.member("referencedColumnName", name));
        annotation.stringValue("columnDefinition").ifPresent(name -> builder.member("columnDefinition", name));
        return Collections.singletonList(builder.build());
    }

}
