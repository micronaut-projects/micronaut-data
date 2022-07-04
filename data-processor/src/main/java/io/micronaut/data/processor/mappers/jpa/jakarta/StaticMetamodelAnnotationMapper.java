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
package io.micronaut.data.processor.mappers.jpa.jakarta;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.annotation.AnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.persistence.metamodel.StaticMetamodel;

import java.util.Collections;
import java.util.List;

/**
 * Mapping {@link StaticMetamodel} to have reflective access.
 *
 * @author Denis Stepanov
 * @since 3.5
 */
public class StaticMetamodelAnnotationMapper implements AnnotationMapper<StaticMetamodel> {

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<StaticMetamodel> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(ReflectiveAccess.class).build());
    }

}
