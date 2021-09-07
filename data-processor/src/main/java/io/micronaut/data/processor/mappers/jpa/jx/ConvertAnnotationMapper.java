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
package io.micronaut.data.processor.mappers.jpa.jx;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA Convert annotation.
 *
 * @author Denis Stepanov
 * @since 3.1.0
 */
public class ConvertAnnotationMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Convert";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> convertAnnotation, VisitorContext visitorContext) {
        String converter = convertAnnotation.stringValue("converter").orElse(null);
        if (converter == null || converter.equals(void.class.getName()) || converter.equals(Object.class.getName())) {
            visitorContext.fail("Missing converter element " + converter, null);
            return Collections.emptyList();
        }
        if (convertAnnotation.stringValue("attributeName").isPresent()) {
            visitorContext.fail("@Convert value 'attributeName' not supported", null);
        }
        if (convertAnnotation.stringValue("disableConversion").isPresent()) {
            visitorContext.fail("@Convert value 'disableConversion' not supported", null);
        }
        return Collections.singletonList(
                AnnotationValue.builder(MappedProperty.class).member("converter", new AnnotationClassValue<>(converter)).build()
        );
    }

}
