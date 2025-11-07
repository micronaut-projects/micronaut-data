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
import io.micronaut.data.processor.visitors.finders.TypeUtils;

import java.util.regex.Matcher;

/**
 * Find all specification method.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindAllSpecificationMethodMatcher extends AbstractSpecificationMethodMatcher {

    /**
     * Find one method.
     */
    public FindAllSpecificationMethodMatcher() {
        super("get", "find", "search", "query");
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 300;
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, Matcher matcher) {
        if (TypeUtils.doesMethodProducesIterable(matchContext.getMethodElement()) && isQuerySpecification(matchContext)) {
            FindersUtils.InterceptorMatch e = FindersUtils.pickFindAllSpecInterceptor(matchContext, matchContext.getReturnType());
            return mc -> new MethodMatchInfo(DataMethod.OperationType.QUERY, e.returnType(), e.interceptor());
        }
        return null;
    }

}
