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
package io.micronaut.data.processor.mappers.jta;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import io.micronaut.core.annotation.NonNull;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Maps JTA's transaction annotation.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class JtaTransactionalMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "javax.transaction.Transactional";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<Annotation> builder =
                AnnotationValue.builder("io.micronaut.transaction.annotation.Transactional");
        annotation.getValue(String.class).ifPresent(type ->
                    builder.member("propagation", type)
        );
        annotation.get("rollbackOn", String[].class).ifPresent(type -> {
                    AnnotationClassValue[] classValues = Arrays.stream(type).map(AnnotationClassValue::new).toArray(AnnotationClassValue[]::new);
                    builder.member("rollbackFor", classValues);
                }
        );
        annotation.get("dontRollbackOn", String[].class).ifPresent(type -> {
                    AnnotationClassValue[] classValues = Arrays.stream(type).map(AnnotationClassValue::new).toArray(AnnotationClassValue[]::new);
                    builder.member("noRollbackFor", classValues);
                }
        );
        return Collections.singletonList(
                builder.build()
        );
    }
}
