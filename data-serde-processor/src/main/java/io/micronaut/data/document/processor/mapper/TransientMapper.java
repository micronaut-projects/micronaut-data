/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.data.annotation.Transient;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.Collections;
import java.util.List;

/**
 * Serdeable mapper of {@link Transient} which marks fields annotated with {@link Transient} as serde ignored.
 *
 * @author radovanradic
 * @version 4.0.0.
 */
public class TransientMapper implements TypedAnnotationMapper<Transient> {

    @Override
    public Class<Transient> annotationType() {
        return Transient.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Transient> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
            AnnotationValue.builder(SerdeConfig.class)
                .member(SerdeConfig.IGNORED, true)
                .build()
        );
    }

}
