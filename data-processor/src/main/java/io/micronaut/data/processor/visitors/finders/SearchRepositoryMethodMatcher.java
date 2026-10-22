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
import io.micronaut.data.annotation.Join;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;
import io.micronaut.data.repository.SearchRepository;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.ProcessingException;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Matches the {@link SearchRepository} methods {@code search(String)}, {@code search(String, Pageable)}
 * and {@code countHits(String)}, implementing them as if they were named {@code searchBySearch} and
 * {@code countBySearch}: a text match over every {@code @SearchField} property ordered by relevance.
 *
 * @since 5.4.0
 */
@Internal
public final class SearchRepositoryMethodMatcher implements MethodMatcher {

    private static final String SEARCH = "search";
    private static final String COUNT_HITS = "countHits";

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 200;
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        String methodName = matchContext.getMethodElement().getName();
        if (!SEARCH.equals(methodName) && !COUNT_HITS.equals(methodName)) {
            return null;
        }
        List<ParameterElement> parameters = matchContext.getParametersNotInRole();
        if (parameters.size() != 1 || !parameters.get(0).getType().isAssignable(String.class)) {
            return null;
        }
        if (!matchContext.hasRootEntity()) {
            throw new ProcessingException(matchContext.getMethodElement(), "Repository does not have a well-defined primary entity type");
        }
        if (SEARCH.equals(methodName)) {
            ClassElement returnType = matchContext.getReturnType();
            if (TypeUtils.isReactiveOrFuture(returnType)) {
                returnType = returnType.getFirstTypeArgument().orElse(returnType);
            }
            if (!returnType.getName().equals(SearchResults.class.getName())) {
                return null;
            }
            return new QueryCriteriaMethodMatch(List.of(
                new MethodNameParser.Match(QueryMatchId.PREFIX, SEARCH),
                new MethodNameParser.Match(QueryMatchId.PREDICATE, TextSearchSupport.KEYWORD)
            ));
        }
        if (!TypeUtils.isValidCountReturnType(matchContext)) {
            return null;
        }
        return new QueryCriteriaMethodMatch(List.of(
            new MethodNameParser.Match(QueryMatchId.PREFIX, "count"),
            new MethodNameParser.Match(QueryMatchId.PREDICATE, TextSearchSupport.KEYWORD)
        )) {
            @Override
            protected PersistentEntityCriteriaQuery<Object> createQuery(MethodMatchContext matchContext,
                                                                        PersistentEntityCriteriaBuilder cb,
                                                                        List<AnnotationValue<Join>> joinSpecs) {
                return super.createDefaultCountQuery(matchContext, cb, joinSpecs);
            }

            @Override
            protected FindersUtils.InterceptorMatch resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
                return FindersUtils.pickCountInterceptor(matchContext, matchContext.getReturnType());
            }

            @Override
            protected DataMethod.OperationType getOperationType() {
                return DataMethod.OperationType.COUNT;
            }
        };
    }
}
