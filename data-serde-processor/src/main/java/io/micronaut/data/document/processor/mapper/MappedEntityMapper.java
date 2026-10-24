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
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Arrays;
import java.util.List;

/**
 * Serdeable mapper of {@link MappedEntity}.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
public class MappedEntityMapper implements TypedAnnotationMapper<MappedEntity> {

    @Override
    public Class<MappedEntity> annotationType() {
        return MappedEntity.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<MappedEntity> annotation, VisitorContext visitorContext) {
        return Arrays.asList(
                AnnotationValue.builder(Serdeable.Serializable.class).member("enabled", true).build(),
                AnnotationValue.builder(Serdeable.Deserializable.class).member("enabled", true).build()
        );
    }

}
