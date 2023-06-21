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
import io.micronaut.inject.ast.ClassElement;

import java.util.Map;

/**
 * Implementation of {@code count(Specification)} for JPA specifications.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class CountSpecificationMethodMatcher extends AbstractSpecificationMethodMatcher {

    /**
     * Find one method.
     */
    public CountSpecificationMethodMatcher() {
        super("count");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        if (TypeUtils.isValidCountReturnType(matchContext)) {
            return mc -> {
                if (isFirstParameterMicronautDataQuerySpecification(matchContext.getMethodElement())) {
                    Map.Entry<ClassElement, ClassElement> e = FindersUtils.pickCountSpecInterceptor(matchContext, matchContext.getReturnType());
                    return new MethodMatchInfo(DataMethod.OperationType.COUNT, e.getKey(), e.getValue());
                }
                if (isFirstParameterSpringJpaSpecification(mc.getMethodElement())) {
                    return new MethodMatchInfo(
                            DataMethod.OperationType.COUNT,
                            mc.getReturnType(),
                            getInterceptorElement(mc, "io.micronaut.data.spring.jpa.intercept.CountSpecificationInterceptor")
                    );
                }
                ClassElement classElement = getInterceptorElement(mc, "io.micronaut.data.jpa.repository.intercept.CountSpecificationInterceptor");
                return new MethodMatchInfo(
                        DataMethod.OperationType.COUNT,
                        mc.getReturnType(),
                        classElement
                );
            };
        }
        return null;
    }

    @Override
    protected boolean isMatchesParameters(MethodMatchContext matchContext) {
        return super.isMatchesParameters(matchContext) || isFirstParameterMicronautDataQuerySpecification(matchContext.getMethodElement());
    }
}

