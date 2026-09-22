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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.inject.ast.ClassElement;

import java.util.Arrays;

/**
 * The match utils.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class MatchUtils {

    private MatchUtils() {
    }

    /**
     * Analyze method result.
     *
     * @param repositoryClass            The repository class
     * @param rootEntity                 The root entity
     * @param queryResultType            The query result type
     * @param interceptorMatch           The interceptor match
     * @param allowEntityResultByDefault True if entity result is allowed by default
     * @return The method result
     */
    public static MethodResult analyzeMethodResult(ClassElement repositoryClass,
                                                   ClassElement rootEntity,
                                                   @Nullable ClassElement queryResultType,
                                                   FindersUtils.InterceptorMatch interceptorMatch,
                                                   boolean allowEntityResultByDefault) {
        ClassElement resultType = interceptorMatch.returnType();

        if (resultType == null || queryResultType == null || isVoid(resultType) || rootEntity.equals(resultType)) {
            return new MethodResult(resultType, false, false);
        }

        boolean isRuntimeDto = false;
        boolean isDto = isIsDto(repositoryClass, queryResultType, resultType);

        if (isDto) {
            isRuntimeDto = isDtoType(repositoryClass, resultType);
        } else if (interceptorMatch.validateReturnType()) {
            if (TypeUtils.areTypesCompatible(resultType, queryResultType)) {
                if (!queryResultType.isPrimitive()) {
                    resultType = queryResultType;
                }
            } else if (!allowEntityResultByDefault) {
                throw new MatchFailedException("Query results in a type [" + queryResultType.getName() + "] whilst method returns an incompatible type: " + resultType.getName());
            }
        }
        return new MethodResult(resultType, isDto, isRuntimeDto);
    }

    private static boolean isIsDto(ClassElement repositoryClass,
                                   ClassElement queryResultType,
                                   ClassElement resultType) {
        if (isObjectArrayResult(resultType)) {
            return true;
        }
        if (TypeUtils.areTypesCompatible(resultType, queryResultType)) {
            return false;
        }
        return isDtoType(repositoryClass, resultType)
            || isDto(queryResultType, resultType);
    }

    public static boolean isDto(ClassElement entityType, ClassElement resultType) {
        return resultType.hasStereotype(Introspected.class) && entityType.hasStereotype(MappedEntity.class)
            || isObjectArrayResult(resultType); // Allow Object[] as a DTO
    }

    private static boolean isObjectArrayResult(ClassElement resultType) {
        return resultType.isArray() && (resultType.getName().equals(Object.class.getName()) || resultType.getName().equals(Object[].class.getName()));
    }

    private static boolean isVoid(ClassElement resultType) {
        return resultType.isAssignable(void.class) || resultType.isAssignable(Void.class) || resultType.getName().equals("kotlin.Unit");
    }

    private static boolean isDtoType(ClassElement repositoryElement, ClassElement classElement) {
        return Arrays.stream(repositoryElement.stringValues(RepositoryConfiguration.class, "queryDtoTypes"))
            .anyMatch(type -> classElement.getName().equals(type));
    }

}
