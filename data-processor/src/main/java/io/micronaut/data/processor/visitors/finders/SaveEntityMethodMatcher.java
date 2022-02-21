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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.processor.visitors.AnnotationMetadataHierarchy;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Arrays;
import java.util.Map;

import static io.micronaut.data.processor.visitors.finders.FindersUtils.getInterceptorElement;

/**
 * A save method for saving a single entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveEntityMethodMatcher extends AbstractPrefixPatternMethodMatcher {

    public static final String[] PREFIXES = {"save", "persist", "store", "insert"};

    /**
     * The default constructor.
     */
    public SaveEntityMethodMatcher() {
        super(PREFIXES);
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        ParameterElement[] parameters = matchContext.getParameters();
        if (parameters.length > 0 &&
                Arrays.stream(parameters)
                        .anyMatch(p -> (TypeUtils.isIterableOfEntity(p.getGenericType()) || TypeUtils.isEntity(p.getGenericType())) && isValidSaveReturnType(matchContext))) {
            return mc -> {
                ParameterElement[] parameters1 = mc.getParameters();
                ParameterElement entityParameter = Arrays.stream(parameters1).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
                ParameterElement entitiesParameter = Arrays.stream(parameters1).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
                if (entityParameter == null && entitiesParameter == null) {
                    throw new MatchFailedException("Cannot implement save method for specified arguments and return type", mc.getMethodElement());
                }
                Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.resolveInterceptorTypeByOperationType(
                        entityParameter != null,
                        entitiesParameter != null,
                        DataMethod.OperationType.INSERT, mc
                );
                MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                        DataMethod.OperationType.INSERT,
                        entry.getKey(),
                        getInterceptorElement(mc, entry.getValue())
                );
                if (!mc.supportsImplicitQueries()) {
                    final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                            mc.getRepositoryClass().getAnnotationMetadata(),
                            mc.getAnnotationMetadata()
                    );
                    methodMatchInfo
                            .encodeEntityParameters(true)
                            .queryResult(
                                    mc.getQueryBuilder().buildInsert(annotationMetadataHierarchy, mc.getRootEntity())
                            );
                }
                if (entitiesParameter != null) {
                    methodMatchInfo.addParameterRole(TypeRole.ENTITIES, entitiesParameter.getName());
                }
                if (entityParameter != null) {
                    methodMatchInfo.addParameterRole(TypeRole.ENTITY, entityParameter.getName());
                }
                return methodMatchInfo;
            };
        }
        return null;
    }

    /**
     * Is the return type valid for saving an entity.
     *
     * @param matchContext The match context
     * @return True if correct return type
     */
    static boolean isValidSaveReturnType(@NonNull MatchContext matchContext) {
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isVoid(returnType) || TypeUtils.isNumber(returnType)) {
            return true;
        }

        if (TypeUtils.isReactiveOrFuture(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(null);
        }
        if (returnType == null) {
            // Skip for Completable etc.
            return true;
        }
        if (TypeUtils.isNumber(returnType) || TypeUtils.isIterableOfEntity(returnType)) {
            return true;
        } else if (returnType.isAssignable(Iterable.class)) {
            // This doesn't return correct class for generic type 'S':
            // <S extends E> CompletableFuture<? extends Iterable<S>> saveAll(@Valid @NotNull @NonNull Iterable<S> entities)
            return true;
        }
        return TypeUtils.isEntity(returnType);
    }

}
