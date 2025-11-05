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
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

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

    private boolean isSpringPredicateSpecification(@NonNull MethodElement methodElement) {
        return isFirstParameterAssignable(methodElement, "org.springframework.data.jpa.domain.Specification");
    }

    protected final boolean isQuerySpecification(@NonNull MethodElement methodElement) {
        if (isPredicateSpecification(methodElement) || isSpringPredicateSpecification(methodElement)) {
            return true;
        }
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.QuerySpecification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder");
    }

    protected final boolean isDeleteSpecification(@NonNull MethodElement methodElement) {
        if (isPredicateSpecification(methodElement) || isSpringPredicateSpecification(methodElement)) {
            return true;
        }
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.DeleteSpecification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder");
    }

    protected final boolean isUpdateSpecification(@NonNull MethodElement methodElement) {
        if (isPredicateSpecification(methodElement) || isSpringPredicateSpecification(methodElement)) {
            return true;
        }
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.UpdateSpecification")
            || isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder");
    }

    private boolean isPredicateSpecification(@NonNull MethodElement methodElement) {
        return isFirstParameterAssignable(methodElement, "io.micronaut.data.repository.jpa.criteria.PredicateSpecification");
    }

    private boolean isFirstParameterAssignable(@NonNull MethodElement methodElement, String clazz) {
        final ParameterElement[] parameters = methodElement.getParameters();
        return parameters.length > 0 && parameters[0].getType().isAssignable(clazz);
    }

}
