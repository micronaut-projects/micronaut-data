/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Matcher for vector-search repository methods that start with {@code search...} and return
 * {@link SearchResults}. Methods that start with {@code search...} but do not return
 * {@link SearchResults} are handled by {@link FindMethodMatcher} instead.
 */
@Internal
public final class VectorSearchMethodMatcher extends AbstractMethodMatcher {

    public VectorSearchMethodMatcher() {
        super(MethodNameParser.builder()
            .match(QueryMatchId.PREFIX, "search")
            .tryMatchLastOccurrencePrefixed(QueryMatchId.ORDER, "Order property not specified!", ORDER_VARIATIONS)
            .tryMatchFirstOccurrencePrefixed(QueryMatchId.PREDICATE, BY)
            .takeRest(QueryMatchId.PROJECTION)
            .build());
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        ClassElement returnType = matchContext.getReturnType();
        if (TypeUtils.isReactiveOrFuture(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(returnType);
        }
        if (!returnType.getName().equals(SearchResults.class.getName())) {
            return null;
        }
        return new QueryCriteriaMethodMatch(matches) {
            @Override
            protected PersistentEntityCriteriaQuery<Object> createQuery(MethodMatchContext matchContext,
                                                                        PersistentEntityCriteriaBuilder cb,
                                                                        List joinSpecs) {
                if (!matchContext.hasRootEntity()) {
                    throw new ProcessingException(matchContext.getMethodElement(), "Repository does not have a well-defined primary entity type");
                }
                return super.createQuery(matchContext, cb, joinSpecs);
            }
        };
    }
}
