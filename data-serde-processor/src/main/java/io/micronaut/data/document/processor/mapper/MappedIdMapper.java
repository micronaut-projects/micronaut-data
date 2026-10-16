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
import io.micronaut.data.annotation.Id;
import io.micronaut.data.document.serde.IdDeserializer;
import io.micronaut.data.document.serde.IdPropertyNamingStrategy;
import io.micronaut.data.document.serde.IdSerializer;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.Collections;
import java.util.List;

/**
 * Serdeable mapper of {@link Id}.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
public class MappedIdMapper implements TypedAnnotationMapper<Id> {

    @Override
    public Class<Id> annotationType() {
        return Id.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Id> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.class)
                        .member(SerdeConfig.SERIALIZER_CLASS, IdSerializer.class.getName())
                        .member(SerdeConfig.DESERIALIZER_CLASS, IdDeserializer.class.getName())
                        .member(SerdeConfig.RUNTIME_NAMING, IdPropertyNamingStrategy.class.getName())
                        .build()
        );
    }

}
