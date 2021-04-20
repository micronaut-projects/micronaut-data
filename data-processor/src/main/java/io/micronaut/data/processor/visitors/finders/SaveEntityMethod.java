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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A save method for saving a single entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveEntityMethod extends AbstractPatternBasedMethod {

    public static final Pattern METHOD_PATTERN = Pattern.compile("^((save|persist|store|insert)(\\S*?))$");

    /**
     * The default constructor.
     */
    public SaveEntityMethod() {
        super(METHOD_PATTERN);
    }

    @NonNull
    @Override
    protected MethodMatchInfo.OperationType getOperationType() {
        return MethodMatchInfo.OperationType.INSERT;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return parameters.length > 0 &&
                Arrays.stream(parameters)
                        .anyMatch(p -> (TypeUtils.isIterableOfEntity(p.getGenericType()) || TypeUtils.isEntity(p.getGenericType())) && isValidSaveReturnType(matchContext))
                && super.isMethodMatch(methodElement, matchContext);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        VisitorContext visitorContext = matchContext.getVisitorContext();
        ParameterElement[] parameters = matchContext.getParameters();
        Optional<ParameterElement> entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst();
        if (entityParameter.isPresent()) {
            Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.resolveInterceptorTypeByOperationType(
                    true, false, MethodMatchInfo.OperationType.INSERT, matchContext);
            QueryModel queryModel = matchContext.supportsImplicitQueries() ? null : QueryModel.from(matchContext.getRootEntity());
            MethodMatchInfo methodMatchInfo = new MethodMatchInfo(entry.getKey(),
                    queryModel,
                    getInterceptorElement(matchContext, entry.getValue()),
                    MethodMatchInfo.OperationType.INSERT
            );
            methodMatchInfo.addParameterRole(TypeRole.ENTITY, entityParameter.get().getName());
            return methodMatchInfo;
        }
        Optional<ParameterElement> entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst();
        if (entitiesParameter.isPresent()) {
            Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.resolveInterceptorTypeByOperationType(
                    false, true, MethodMatchInfo.OperationType.INSERT, matchContext);
            QueryModel queryModel = matchContext.supportsImplicitQueries() ? null : QueryModel.from(matchContext.getRootEntity());
            MethodMatchInfo methodMatchInfo = new MethodMatchInfo(entry.getKey(),
                    queryModel,
                    getInterceptorElement(matchContext, entry.getValue()),
                    MethodMatchInfo.OperationType.INSERT
            );
            methodMatchInfo.addParameterRole(TypeRole.ENTITIES, entitiesParameter.get().getName());
            return methodMatchInfo;
        }
        visitorContext.fail("Cannot implement save method for specified arguments and return type", matchContext.getMethodElement());
        return null;
    }

    /**
     * Is the return type valid for saving an entity.
     * @param matchContext The match context
     * @return True if correct return type
     */
    static boolean isValidSaveReturnType(@NonNull MatchContext matchContext) {
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isVoid(returnType)) {
            return true;
        }

        if (TypeUtils.isReactiveOrFuture(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(null);
        }
        if (returnType == null) {
            // Skip for Completable etc.
            return true;
        }
        if (TypeUtils.isIterableOfEntity(returnType)) {
            return true;
        } else if (returnType.isAssignable(Iterable.class)) {
            // This doesn't return correct class for generic type 'S':
            // <S extends E> CompletableFuture<? extends Iterable<S>> saveAll(@Valid @NotNull @NonNull Iterable<S> entities)
            return true;
        }
        return TypeUtils.isEntity(returnType);
    }

}
