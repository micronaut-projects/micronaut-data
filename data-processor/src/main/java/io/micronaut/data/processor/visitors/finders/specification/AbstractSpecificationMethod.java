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
package io.micronaut.data.processor.visitors.finders.specification;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractListMethod;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Abstract superclass for specification methods.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
public class AbstractSpecificationMethod extends AbstractListMethod {

    /**
     * Default constructor.
     *
     * @param prefixes The method prefixes to match
     */
    protected AbstractSpecificationMethod(String... prefixes) {
        super(prefixes);
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 200;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return isFirstParameterSpringJpaSpecification(methodElement, matchContext)
                || isFirstParameterMicronautJpaSpecification(methodElement);
    }

    private boolean isFirstParameterSpringJpaSpecification(MethodElement methodElement, MatchContext matchContext) {
        VisitorContext visitorContext = matchContext.getVisitorContext();
        if (visitorContext.getClassElement("io.micronaut.data.spring.jpa.intercept.CountSpecificationInterceptor").isPresent() &&
                visitorContext.getClassElement("org.springframework.data.jpa.domain.Specification").isPresent()) {
            return isFirstParameterSpringJpaSpecification(methodElement);
        }
        return false;
    }

    protected final boolean isFirstParameterSpringJpaSpecification(@NonNull MethodElement methodElement) {
        final ParameterElement[] parameters = methodElement.getParameters();
        return parameters.length > 0 &&
                parameters[0].getType().isAssignable("org.springframework.data.jpa.domain.Specification");
    }

    protected final boolean isFirstParameterMicronautJpaSpecification(@NonNull MethodElement methodElement) {
        final ParameterElement[] parameters = methodElement.getParameters();
        return parameters.length > 0 &&
                parameters[0].getType().isAssignable("io.micronaut.data.jpa.repository.criteria.Specification");
    }

}
