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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;

/**
 * Abstract superclass for specification methods.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Experimental
public abstract class AbstractSpecificationMethodMatcher extends AbstractPrefixPatternMethodMatcher {

    /**
     * Default constructor.
     *
     * @param prefixes The method prefixes to match
     */
    protected AbstractSpecificationMethodMatcher(String... prefixes) {
        super(Arrays.asList(prefixes));
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 200;
    }

    @Override
    public MethodMatch match(MethodMatchContext matchContext) {
        if (!isMatchesParameters(matchContext)) {
            return null;
        }
        return super.match(matchContext);
    }

    /**
     * @param matchContext    The match context
     * @param interceptorType The interceptor type
     * @return The resolved class element
     */
    protected final ClassElement getInterceptorElement(MethodMatchContext matchContext, String interceptorType) {
        return FindersUtils.getInterceptorElement(matchContext, interceptorType);
    }

    /**
     * Is matches parameters.
     *
     * @param matchContext The context
     * @return true if matches
     */
    protected boolean isMatchesParameters(MethodMatchContext matchContext) {
        return isFirstParameterSpringJpaSpecification(matchContext.getMethodElement(), matchContext) ||
            isFirstParameterMicronautJpaSpecification(matchContext.getMethodElement());
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
        return isFirstParameterAssignable(methodElement, "org.springframework.data.jpa.domain.Specification");
    }

    protected final boolean isFirstParameterMicronautJpaSpecification(@NonNull MethodElement methodElement) {
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.jpa.repository.criteria.Specification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.hibernate6.jpa.repository.criteria.Specification");
    }

    protected final boolean isFirstParameterMicronautDataQuerySpecification(@NonNull MethodElement methodElement) {
        if (isFirstParameterMicronautDataPredicateSpecification(methodElement)) {
            return true;
        }
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.QuerySpecification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder");
    }

    protected final boolean isFirstParameterMicronautDataDeleteSpecification(@NonNull MethodElement methodElement) {
        if (isFirstParameterMicronautDataPredicateSpecification(methodElement)) {
            return true;
        }
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.DeleteSpecification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder");
    }

    protected final boolean isFirstParameterMicronautDataUpdateSpecification(@NonNull MethodElement methodElement) {
        if (isFirstParameterMicronautDataPredicateSpecification(methodElement)) {
            return true;
        }
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.UpdateSpecification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder");
    }

    private boolean isFirstParameterMicronautDataPredicateSpecification(@NonNull MethodElement methodElement) {
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.PredicateSpecification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.hibernate6.jpa.criteria.PredicateSpecification");
    }

    private boolean isFirstParameterAssignable(@NonNull MethodElement methodElement, String clazz) {
        final ParameterElement[] parameters = methodElement.getParameters();
        return parameters.length > 0 && parameters[0].getType().isAssignable(clazz);
    }

}
