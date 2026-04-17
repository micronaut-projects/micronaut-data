/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Javax persistent access annotation mapper.
 */
public class AccessAnnotationMapper implements NamedAnnotationMapper {

    static final String MEMBER_INTROSPECTED_ACCESS_KIND = "accessKind";
    static final String MEMBER_INTROSPECTED_VISIBILITY = "visibility";

    enum JavaxAccessType {
        FIELD,
        PROPERTY
    }

    @Override
    public String getName() {
        return "javax.persistence.Access";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        JavaxAccessType accessType = annotation.getRequiredValue(JavaxAccessType.class);

        AnnotationValueBuilder<Introspected> introspectedBuilder = AnnotationValue.builder(Introspected.class);

        if (accessType == JavaxAccessType.FIELD) {
            introspectedBuilder.member(MEMBER_INTROSPECTED_ACCESS_KIND, new Introspected.AccessKind[]{
                Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD
            }).member(MEMBER_INTROSPECTED_VISIBILITY, Introspected.Visibility.ANY);
        } else {
            introspectedBuilder.member(MEMBER_INTROSPECTED_ACCESS_KIND, Introspected.AccessKind.METHOD);
        }
        return Collections.singletonList(introspectedBuilder.build());
    }

}
