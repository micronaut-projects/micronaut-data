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
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA's {@code JoinTable} annotation to Micronaut's.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
public final class JoinTableAnnotationMapper implements NamedAnnotationMapper {

    private final static JoinColumnAnnotationMapper JOIN_COLUMN_ANNOTATION_MAPPER = new JoinColumnAnnotationMapper();

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.JoinTable";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder("io.micronaut.data.jdbc.annotation.JoinTable");
        annotation.stringValue("name").ifPresent(name -> builder.member("name", name));
        AnnotationValue<?>[] joinColumns = annotation.getAnnotations("joinColumns")
                .stream()
                .flatMap(ann -> JOIN_COLUMN_ANNOTATION_MAPPER.map(ann, visitorContext).stream())
                .toArray(AnnotationValue[]::new);
        if (joinColumns.length > 0) {
            builder.member("joinColumns", joinColumns);
        }
        AnnotationValue<?>[] inverseJoinColumns = annotation.getAnnotations("inverseJoinColumns")
                .stream()
                .flatMap(ann -> JOIN_COLUMN_ANNOTATION_MAPPER.map(ann, visitorContext).stream())
                .toArray(AnnotationValue[]::new);
        if (inverseJoinColumns.length > 0) {
            builder.member("inverseJoinColumns", inverseJoinColumns);
        }
        annotation.stringValue(SqlMembers.CATALOG).ifPresent(catalog -> builder.member(SqlMembers.CATALOG, catalog));
        annotation.stringValue(SqlMembers.SCHEMA).ifPresent(catalog -> builder.member(SqlMembers.SCHEMA, catalog));
        return Collections.singletonList(builder.build());
    }

}
