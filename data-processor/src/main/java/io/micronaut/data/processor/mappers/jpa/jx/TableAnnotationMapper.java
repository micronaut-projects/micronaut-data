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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Maps JPA's {@code Table} annotation to {@link MappedEntity}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class TableAnnotationMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Table";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {

        IndexAnnotationMapper mapper = new IndexAnnotationMapper();

        final AnnotationValueBuilder<MappedEntity> builder = AnnotationValue.builder(MappedEntity.class);
        annotation.stringValue("name").ifPresent(builder::value);
        annotation.stringValue(SqlMembers.CATALOG).ifPresent(catalog -> builder.member(SqlMembers.CATALOG, catalog));
        annotation.stringValue("schema").ifPresent(schema -> builder.member("schema", schema));
        final AnnotationValueBuilder<Indexes> idxBuilder = AnnotationValue.builder(Indexes.class);

        List<AnnotationValue<Annotation>> indexesValue = annotation.getAnnotations("indexes");
        if (CollectionUtils.isNotEmpty(indexesValue)) {
            final AnnotationValue<Index>[] annotationValues =
                (AnnotationValue<Index>[]) indexesValue.stream()
                    .map(a -> mapper.map(a, null)
                        .get(0)).toArray(AnnotationValue[]::new);
            idxBuilder.member("value", annotationValues);
        }

        return Arrays.asList(builder.build(), idxBuilder.build());
    }
}
