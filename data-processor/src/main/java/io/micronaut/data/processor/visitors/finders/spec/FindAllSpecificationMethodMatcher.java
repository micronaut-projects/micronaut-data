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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractSpecificationMethodMatcher;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Map;

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
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        if (TypeUtils.isValidFindAllReturnType(matchContext) && isCorrectParameters(matchContext.getMethodElement())) {
            if (isFirstParameterMicronautDataQuerySpecification(matchContext.getMethodElement())) {
                Map.Entry<ClassElement, ClassElement> e = FindersUtils.pickFindAllSpecInterceptor(matchContext, matchContext.getReturnType());
                return mc -> new MethodMatchInfo(DataMethod.OperationType.QUERY, e.getKey(), e.getValue());
            }
            if (isFirstParameterSpringJpaSpecification(matchContext.getMethodElement())) {
                return mc -> new MethodMatchInfo(
                        DataMethod.OperationType.QUERY,
                        mc.getReturnType(),
                        getInterceptorElement(mc, "io.micronaut.data.spring.jpa.intercept.FindAllSpecificationInterceptor")
                );
            }
            return mc -> {
                ClassElement classElement;
                try {
                    classElement = getInterceptorElement(mc, "io.micronaut.data.jpa.repository.intercept.FindAllSpecificationInterceptor");
                } catch (IllegalStateException e) {
                    classElement = getInterceptorElement(mc, "io.micronaut.data.hibernate6.jpa.repository.intercept.FindAllSpecificationInterceptor");
                }
                return new MethodMatchInfo(
                    DataMethod.OperationType.QUERY,
                    mc.getReturnType(),
                    classElement);
            };
        }
        return null;
    }

    private boolean isCorrectParameters(@NonNull MethodElement methodElement) {
        final ParameterElement[] parameters = methodElement.getParameters();
        final int len = parameters.length;
        switch (len) {
            case 1:
                return true;
            case 2:
                return parameters[1].getType().isAssignable("org.springframework.data.domain.Sort") ||
                        parameters[1].getType().isAssignable("io.micronaut.data.model.Sort");
            default:
                return false;
        }
    }

    @Override
    protected boolean isMatchesParameters(MethodMatchContext matchContext) {
        return super.isMatchesParameters(matchContext) || isFirstParameterMicronautDataQuerySpecification(matchContext.getMethodElement());
    }
}
