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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.document.serde.ManyRelationSerializer;
import io.micronaut.data.document.serde.OneRelationDeserializer;
import io.micronaut.data.document.serde.OneRelationSerializer;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.Collections;
import java.util.List;

/**
 * Serdeable mapper of {@link Relation}.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
public class MappedRelationMapper implements TypedAnnotationMapper<Relation> {

    @Override
    public Class<Relation> annotationType() {
        return Relation.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Relation> annotation, VisitorContext visitorContext) {
        Relation.Kind kind = annotation.getRequiredValue(Relation.Kind.class);
        if (kind == Relation.Kind.MANY_TO_ONE || kind == Relation.Kind.ONE_TO_ONE) {
            return Collections.singletonList(
                    AnnotationValue.builder(SerdeConfig.class)
                            .member(SerdeConfig.SERIALIZER_CLASS, OneRelationSerializer.class)
                            .member(SerdeConfig.DESERIALIZER_CLASS, OneRelationDeserializer.class)
                            .build()
            );
        } else if (kind == Relation.Kind.EMBEDDED) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.class)
                        .member(SerdeConfig.SERIALIZER_CLASS, ManyRelationSerializer.class)
                        .build()
        );
    }

}
