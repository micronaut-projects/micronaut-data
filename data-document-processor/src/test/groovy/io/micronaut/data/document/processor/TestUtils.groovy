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
package io.micronaut.data.document.processor

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.model.DataType
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.inject.ast.ClassElement

import java.util.function.Function
import java.util.stream.Stream

@CompileStatic
class TestUtils {

    static SourcePersistentEntity sourcePersistentEntity(ClassElement ce) {
        return new SourcePersistentEntity(ce, new Function<ClassElement, SourcePersistentEntity>() {
            @Override
            SourcePersistentEntity apply(ClassElement classElement) {
                return new SourcePersistentEntity(classElement, this);
            }
        })
    }

    static String getQuery(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotation(Query).stringValue().get()
    }

    static String[] getQueryParts(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotationMetadata().stringValues(DataMethod.class, DataMethod.META_MEMBER_EXPANDABLE_QUERY)
    }

    static String getRawQuery(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotation(Query).stringValue( "rawQuery").get()
    }

    static String[] getParameterPropertyPaths(AnnotationMetadataProvider metadata) {
        return getParameterPropertyPaths(metadata.getAnnotation(DataMethod))
    }

    static String[] getQueryParameterNames(AnnotationMetadataProvider metadata) {
        return getQueryParameterNames(metadata.getAnnotation(DataMethod))
    }

    static String[] getParameterAutoPopulatedProperties(AnnotationMetadataProvider metadata) {
        return getParameterAutoPopulatedProperties(metadata.getAnnotation(DataMethod))
    }

    static String[] getParameterRequiresPreviousPopulatedValueProperties(AnnotationMetadataProvider metadata) {
        return getParameterRequiresPreviousPopulatedValueProperties(metadata.getAnnotation(DataMethod))
    }

    static String[] getParameterBindingIndexes(AnnotationMetadataProvider metadata) {
        return getParameterBindingIndexes(metadata.getAnnotation(DataMethod))
    }

    static String[] getParameterBindingPaths(AnnotationMetadataProvider metadata) {
        return getParameterBindingPaths(metadata.getAnnotation(DataMethod))
    }

    static DataType[] getDataTypes(AnnotationMetadataProvider metadata) {
        return getDataTypes(metadata.getAnnotation(DataMethod))
    }

    static DataType[] getDataTypes(AnnotationValue<DataMethod> annotationValue) {
        return annotationValue.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .stream()
                .flatMap(p -> {
                    def o = p.enumValue(DataMethodQueryParameter.META_MEMBER_DATA_TYPE, DataType)
                    if (o.isPresent()) {
                        return Stream.of(o.get())
                    }
                    return Stream.empty()
                })
                .toArray(DataType[]::new)
    }

    static String[] getQueryParameterNames(AnnotationValue<DataMethod> annotationValue) {
        return annotationValue.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .stream()
                .map(p -> p.stringValue(DataMethodQueryParameter.META_MEMBER_NAME).orElse(""))
                .toArray(String[]::new)
    }

    static String[] getParameterAutoPopulatedProperties(AnnotationValue<DataMethod> annotationValue) {
        return annotationValue.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .stream()
                .map(p -> {
                    if (p.booleanValue(DataMethodQueryParameter.META_MEMBER_AUTO_POPULATED).orElse(false)) {
                        return getPropertyPath(p)
                    }
                    return ""
                })
                .toArray(String[]::new)
    }

    static String[] getParameterRequiresPreviousPopulatedValueProperties(AnnotationValue<DataMethod> annotationValue) {
        return annotationValue.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .stream()
                .map(p -> {
                    if (p.booleanValue(DataMethodQueryParameter.META_MEMBER_REQUIRES_PREVIOUS_POPULATED_VALUES).orElse(false)) {
                        return getPropertyPath(p)
                    }
                    return ""
                })
                .toArray(String[]::new)
    }

    static String[] getParameterPropertyPaths(AnnotationValue<DataMethod> annotationValue) {
        return annotationValue.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .stream()
                .map(p -> getPropertyPath(p))
                .toArray(String[]::new)
    }

    private static String getPropertyPath(AnnotationValue<DataMethodQueryParameter> p) {
        def propertyPath
        def prop = p.stringValue(DataMethodQueryParameter.META_MEMBER_PROPERTY)
        if (prop.isPresent()) {
            propertyPath = prop.get()
        } else {
            propertyPath = String.join(".", p.stringValues(DataMethodQueryParameter.META_MEMBER_PROPERTY_PATH))
        }
        return propertyPath
    }

    static String[] getParameterBindingIndexes(AnnotationValue<DataMethod> annotationValue) {
        return annotationValue.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .stream()
                .map(p -> p.intValue(DataMethodQueryParameter.META_MEMBER_PARAMETER_INDEX).orElse(-1).toString())
                .toArray(String[]::new)
    }

    static String[] getParameterBindingPaths(AnnotationValue<DataMethod> annotationValue) {
        return annotationValue.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
                .stream()
                .map(p -> p.stringValue(DataMethodQueryParameter.META_MEMBER_PARAMETER_BINDING_PATH).orElse(""))
                .toArray(String[]::new)
    }
}
