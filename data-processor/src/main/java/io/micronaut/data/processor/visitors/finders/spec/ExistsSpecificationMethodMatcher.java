/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.inject.ast.ClassElement;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * Exists specification method.
 *
 * @author Nick Hensel
 * @since 3.8
 */
@Internal
public final class ExistsSpecificationMethodMatcher extends AbstractSpecificationMethodMatcher {

    public ExistsSpecificationMethodMatcher() {
        super("exists");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, Matcher matcher) {
        if (TypeUtils.doesMethodProducesABoolean(matchContext.getMethodElement())) {
            Map.Entry<ClassElement, ClassElement> e = FindersUtils.pickExistsSpecInterceptor(matchContext, matchContext.getReturnType());
            return mc -> new MethodMatchInfo(DataMethod.OperationType.EXISTS, e.getKey(), e.getValue());
        }
        return null;
    }

    @Override
    protected boolean isMatchesParameters(MethodMatchContext matchContext) {
        return super.isMatchesParameters(matchContext) || isFirstParameterMicronautDataQuerySpecification(matchContext.getMethodElement());
    }
}
