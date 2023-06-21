/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor;
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor;
import io.micronaut.data.intercept.reactive.FindByIdReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindOneReactiveInterceptor;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.AbstractMap;
import java.util.Map;

/**
 * Find method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class FindMethodMatcher extends AbstractPatternMethodMatcher {

    public FindMethodMatcher() {
        super(true, "find", "get", "query", "retrieve", "read", "search");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        if (isCompatibleReturnType(matchContext)) {
            return new QueryCriteriaMethodMatch(matcher) {

                boolean hasIdMatch;

                @Override
                protected <T> void apply(MethodMatchContext matchContext,
                                         PersistentEntityRoot<T> root,
                                         PersistentEntityCriteriaQuery<T> query,
                                         SourcePersistentEntityCriteriaBuilder cb) {
                    super.apply(matchContext, root, query, cb);
                    if (query instanceof AbstractPersistentEntityCriteriaQuery) {
                        hasIdMatch = ((AbstractPersistentEntityCriteriaQuery<T>) query).hasOnlyIdRestriction();
                    }
                }

                @Override
                protected Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
                    Map.Entry<ClassElement, Class<? extends DataInterceptor>> e = super.resolveReturnTypeAndInterceptor(matchContext);
                    Class<? extends DataInterceptor> interceptorType = e.getValue();
                    ClassElement queryResultType = e.getKey();
                    if (isFindByIdQuery(matchContext, queryResultType)) {
                        if (interceptorType == FindOneInterceptor.class) {
                            interceptorType = FindByIdInterceptor.class;
                        } else if (interceptorType == FindOneAsyncInterceptor.class) {
                            interceptorType = FindByIdAsyncInterceptor.class;
                        } else if (interceptorType == FindOneReactiveInterceptor.class) {
                            interceptorType = FindByIdReactiveInterceptor.class;
                        }
                    }
                    return new AbstractMap.SimpleEntry<>(queryResultType, interceptorType);
                }

                private boolean isFindByIdQuery(@NonNull MethodMatchContext matchContext,
                                                @NonNull ClassElement queryResultType) {
                    return hasIdMatch
                            && matchContext.supportsImplicitQueries()
                            && queryResultType.getName().equals(matchContext.getRootEntity().getName())
                            && hasNoWhereAndJoinDeclaration(matchContext);
                }

            };
        }
        return null;
    }

    private boolean isCompatibleReturnType(@NonNull MatchContext matchContext) {
        MethodElement methodElement = matchContext.getMethodElement();
        ClassElement returnType = TypeUtils.getMethodProducingItemType(methodElement);
        if (returnType == null) {
            return false;
        }
        if (!TypeUtils.isVoid(returnType)) {
            return returnType.hasStereotype(Introspected.class) ||
                    returnType.isPrimitive() ||
                    ClassUtils.isJavaBasicType(returnType.getName()) ||
                    TypeUtils.isContainerType(returnType);
        }
        ClassElement genericReturnType = methodElement.getGenericReturnType();
        return matchContext.isTypeInRole(genericReturnType, TypeRole.PAGE) ||
                matchContext.isTypeInRole(genericReturnType, TypeRole.SLICE);
    }

}
