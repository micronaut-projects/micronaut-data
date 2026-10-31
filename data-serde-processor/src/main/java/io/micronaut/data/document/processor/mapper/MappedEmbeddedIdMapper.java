/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.processor.mapper;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.Id;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.Collections;
import java.util.List;

/**
 * Serdeable mapper of {@link Id}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public class MappedEmbeddedIdMapper implements TypedAnnotationMapper<EmbeddedId> {

    private static final String MONGO_ID = "_id";

    @Override
    public Class<EmbeddedId> annotationType() {
        return EmbeddedId.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<EmbeddedId> annotation, VisitorContext visitorContext) {
        // The id is read under its MongoDB name through an alias, and the MongoDB entity codec renames it when
        // writing. A custom serializer, deserializer or runtime naming strategy here would turn serde source
        // generation off for the whole entity
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.class)
                        .member(SerdeConfig.ALIASES, MONGO_ID)
                        .build()
        );
    }

}
