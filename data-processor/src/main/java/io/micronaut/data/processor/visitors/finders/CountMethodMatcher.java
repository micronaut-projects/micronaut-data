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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;
import io.micronaut.inject.ast.ClassElement;
import jakarta.persistence.criteria.Expression;

import java.util.Map;

/**
 * Count method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class CountMethodMatcher extends AbstractPatternMethodMatcher {

    public CountMethodMatcher() {
        super(true, "count");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        if (TypeUtils.isValidCountReturnType(matchContext)) {
            return new QueryCriteriaMethodMatch(matcher) {

                @Override
                protected <T> String applyProjections(String querySequence, PersistentEntityRoot<T> root, PersistentEntityCriteriaQuery<T> query, PersistentEntityCriteriaBuilder cb) {
                    boolean distinct = false;
                    if (querySequence.startsWith("Distinct")) {
                        distinct = true;
                        querySequence = querySequence.substring("Distinct".length());
                    }
                    if (StringUtils.isNotEmpty(querySequence)) {
                        Expression<?> propertyPath = getProperty(root, querySequence);
                        Expression<Long> count = distinct ? cb.countDistinct(propertyPath) : cb.count(propertyPath);
                        query.multiselect(count);
                    } else {
                        // TODO: correct distinct
                        Expression<Long> count = cb.count(root);
                        query.multiselect(count);
                    }
                    return "";
                }

                @Override
                protected Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
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
