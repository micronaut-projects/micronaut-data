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
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;

import java.util.List;

/**
 * Count method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class CountMethodMatcher extends AbstractMethodMatcher {

    public CountMethodMatcher() {
        super(MethodNameParser.builder()
            .match(QueryMatchId.PREFIX, "count")
            .tryMatch(QueryMatchId.ALL, ALL)
            .tryMatch(QueryMatchId.DISTINCT, DISTINCT)
            .tryMatchFirstOccurrencePrefixed(QueryMatchId.PREDICATE, BY)
            .takeRest(QueryMatchId.PROJECTION)
            .build());
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        if (TypeUtils.isValidCountReturnType(matchContext)) {
            return new QueryCriteriaMethodMatch(matches) {

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
        return null;
    }

}
