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
package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Maps Spring's transaction annotation.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SpringTransactionalMapper implements NamedAnnotationMapper {

    @Override
    public final String getName() {
        return "org.springframework.transaction.annotation.Transactional";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final boolean springManagement = visitorContext.getClassElement("io.micronaut.spring.tx.annotation.Transactional").isPresent();
        AnnotationValueBuilder<Annotation> builder =
                AnnotationValue.builder(springManagement ? "io.micronaut.spring.tx.annotation.Transactional" : "io.micronaut.transaction.annotation.TransactionalAdvice");
        annotation.getValue(String.class).ifPresent(s -> {
            builder.value(s);
            builder.member("transactionManager", s);
        });

        Stream.of("propagation", "isolation", "transactionManager")
                .forEach(member -> annotation.get(member, String.class).ifPresent(s -> builder.member(member, s)));
        Stream.of("rollbackForClassName", "noRollbackForClassName")
                .forEach(member -> annotation.get(member, String[].class).ifPresent(s -> builder.member(member, s)));
        Stream.of("rollbackFor", "noRollbackFor")
                .forEach(member -> annotation.get(member, AnnotationClassValue[].class).ifPresent(classValues -> {
                    String[] names = new String[classValues.length];
                    for (int i = 0; i < classValues.length; i++) {
                        AnnotationClassValue classValue = classValues[i];
                        names[i] = classValue.getName();
                    }
                    builder.member(member, names);
                }));
        annotation.get("timeout", Integer.class).ifPresent(integer -> builder.member("timeout", integer));
        annotation.get("readOnly", Boolean.class).ifPresent(bool -> builder.member("readOnly", bool));

        return Collections.singletonList(builder.build());
    }
}
