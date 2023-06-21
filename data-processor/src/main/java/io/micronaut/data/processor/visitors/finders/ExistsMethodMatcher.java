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
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;
import io.micronaut.inject.ast.ClassElement;

import java.util.Map;

/**
 * Exists method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class ExistsMethodMatcher extends AbstractPatternMethodMatcher {

    public ExistsMethodMatcher() {
        super(false, "exists");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        if (TypeUtils.doesMethodProducesABoolean(matchContext.getMethodElement())) {
            return new QueryCriteriaMethodMatch(matcher) {

                @Override
                protected <T> String applyProjections(String querySequence, PersistentEntityRoot<T> root, PersistentEntityCriteriaQuery<T> query, PersistentEntityCriteriaBuilder cb) {
                    query.multiselect(cb.literal(true));
                    return "";
                }

                @Override
                protected Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
                    return FindersUtils.pickExistsInterceptor(matchContext, matchContext.getReturnType());
                }

                @Override
                protected DataMethod.OperationType getOperationType() {
                    return DataMethod.OperationType.EXISTS;
                }

            };
        }
        return null;
    }
}
