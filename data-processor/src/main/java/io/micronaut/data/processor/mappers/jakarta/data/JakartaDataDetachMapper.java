/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.processor.mappers.jakarta.data;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps Jakarta Data @stateful.Detach annotation to the Micronaut interceptor.
 *
 * @author Denis Stepanov
 * @since 5.0
*/
@Internal
public final class JakartaDataDetachMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return "jakarta.data.repository.stateful.Detach";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return List.of(
            AnnotationValue.builder(DataMethod.class)
                .member(DataMethod.META_MEMBER_INTERCEPTOR, new AnnotationClassValue<>("io.micronaut.data.jpa.repository.intercept.DetachInterceptor"))
                .build()
        );
    }
}
