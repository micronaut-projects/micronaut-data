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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor;
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor;
import io.micronaut.data.intercept.reactive.FindByIdReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindOneReactiveInterceptor;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;
import io.micronaut.inject.ast.ClassElement;

import java.util.List;

/**
 * Find method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class FindMethodMatcher extends AbstractMethodMatcher {

    public FindMethodMatcher() {
        super(MethodNameParser.builder()
            .match(QueryMatchId.PREFIX, "find", "list", "get", "query", "retrieve", "read", "search")
            .tryMatch(QueryMatchId.ALL_OR_ONE, ALL_OR_ONE)
            .tryMatchPrefixedNumber(QueryMatchId.LIMIT, TOP_OR_FIRST)
            .tryMatch(QueryMatchId.DISTINCT, DISTINCT)
            .tryMatchLast(QueryMatchId.FOR_UPDATE, FOR_UPDATE)
            .tryMatchLastOccurrencePrefixed(QueryMatchId.ORDER, "Order property not specified!", ORDER_VARIATIONS)
            .tryMatchFirstOccurrencePrefixed(QueryMatchId.PREDICATE, BY)
            .tryMatchExactly(QueryMatchId.FIRST, FIRST) // here we have a bit of conflict between `findFirstProperty` vs `findFirstName`
            .takeRest(QueryMatchId.PROJECTION)
            .build());
    }

    @Override
    public MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        return by(matches);
    }

    public static QueryCriteriaMethodMatch by(List<MethodNameParser.Match> matches) {
        return new QueryCriteriaMethodMatch(matches) {

            boolean hasIdMatch;

            @Override
            protected PersistentEntityCriteriaQuery<Object> createQuery(MethodMatchContext matchContext,
                                                                        PersistentEntityCriteriaBuilder cb,
                                                                        List<AnnotationValue<Join>> joinSpecs) {
                PersistentEntityCriteriaQuery<Object> query = super.createQuery(matchContext, cb, joinSpecs);
                if (query instanceof AbstractPersistentEntityCriteriaQuery<?> criteriaQuery) {
                    hasIdMatch = criteriaQuery.hasOnlyIdRestriction();
                }
                return query;
            }

            @Override
            protected FindersUtils.InterceptorMatch resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
                FindersUtils.InterceptorMatch e = super.resolveReturnTypeAndInterceptor(matchContext);
                ClassElement interceptorType = e.interceptor();
                ClassElement queryResultType = e.returnType();
                if (isFindByIdQuery(matchContext, queryResultType)) {
                    if (interceptorType.isAssignable(FindOneInterceptor.class)) {
                        interceptorType = matchContext.getVisitorContext().getClassElement(FindByIdInterceptor.class).orElseThrow();
                    } else if (interceptorType.isAssignable(FindOneAsyncInterceptor.class)) {
                        interceptorType = matchContext.getVisitorContext().getClassElement(FindByIdAsyncInterceptor.class).orElseThrow();
                    } else if (interceptorType.isAssignable(FindOneReactiveInterceptor.class)) {
                        interceptorType = matchContext.getVisitorContext().getClassElement(FindByIdReactiveInterceptor.class).orElseThrow();
                    }
                    return new FindersUtils.InterceptorMatch(queryResultType, interceptorType);
                }
                return e;
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

}
