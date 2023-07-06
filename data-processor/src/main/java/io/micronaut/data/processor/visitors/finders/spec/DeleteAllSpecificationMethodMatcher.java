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

/**
 * Delete all specification method.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class DeleteAllSpecificationMethodMatcher extends AbstractSpecificationMethodMatcher {

    /**
     * Find one method.
     */
    public DeleteAllSpecificationMethodMatcher() {
        super("delete", "remove", "erase", "eliminate");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        if (TypeUtils.isValidBatchUpdateReturnType(matchContext.getMethodElement())) {
            FindersUtils.InterceptorMatch e = FindersUtils.pickDeleteAllSpecInterceptor(matchContext, matchContext.getReturnType());
            return mc -> new MethodMatchInfo(DataMethod.OperationType.DELETE, e.returnType(), e.interceptor());
        }
        return null;
    }

    @Override
    protected boolean isMatchesParameters(MethodMatchContext matchContext) {
        return isFirstParameterMicronautDataDeleteSpecification(matchContext.getMethodElement());
    }
}
