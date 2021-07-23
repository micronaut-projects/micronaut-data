/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.data.annotation.Index;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Maps JPA's {@code Index} annotation to Micronaut's.
 *
 * @author Davide Pugliese
 * @since 2.4
 */
public class IndexAnnotationMapper implements NamedAnnotationMapper {

    private static final Function<String, String[]> COLUMN_LIST_SPLITTER = cols ->
            Arrays.stream(cols.split(","))
                    .map(String::trim)
                    .toArray(String[]::new);

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Index";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<Index> builder = AnnotationValue.builder(Index.class);
        annotation.stringValue("name").ifPresent(name -> builder.member("name", name));
        annotation.stringValue("columnList")
                .ifPresent(columns -> builder.member("columns", COLUMN_LIST_SPLITTER.apply(columns)));
        annotation.booleanValue("unique").ifPresent(name -> builder.member("unique", name));
        return Collections.singletonList(builder.build());
    }
}
