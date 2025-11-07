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
package io.micronaut.data.processor.visitors.finders.spec;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractSpecificationMethodMatcher;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;

import java.util.regex.Matcher;

/**
 * JPA specification findOne.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindOneSpecificationMethodMatcher extends AbstractSpecificationMethodMatcher {

    /**
     * Find one method.
     */
    public FindOneSpecificationMethodMatcher() {
        super("get", "find", "search", "query");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, Matcher matcher) {
        if (isQuerySpecification(matchContext)) {
            FindersUtils.InterceptorMatch e = FindersUtils.pickFindOneSpecInterceptor(matchContext, matchContext.getMethodElement().getGenericReturnType());
            return mc -> new MethodMatchInfo(DataMethod.OperationType.QUERY, e.returnType(), e.interceptor());
        }
        return null;
    }

}
