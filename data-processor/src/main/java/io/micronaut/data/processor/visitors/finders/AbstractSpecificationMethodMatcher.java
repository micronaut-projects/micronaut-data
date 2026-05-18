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
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.processor.visitors.MethodMatchContext;

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

    protected final boolean isQuerySpecification(MethodMatchContext methodMatchContext) {
        return isPredicateSpecification(methodMatchContext) || methodMatchContext.hasParameterInRole(TypeRole.SPECIFICATION_QUERY);
    }

    protected final boolean isDeleteSpecification(MethodMatchContext methodMatchContext) {
        return isPredicateSpecification(methodMatchContext) || methodMatchContext.hasParameterInRole(TypeRole.SPECIFICATION_DELETE);
    }

    protected final boolean isUpdateSpecification(MethodMatchContext methodMatchContext) {
        return isPredicateSpecification(methodMatchContext) || methodMatchContext.hasParameterInRole(TypeRole.SPECIFICATION_UPDATE);
    }

    private boolean isPredicateSpecification(MethodMatchContext methodMatchContext) {
        return methodMatchContext.hasParameterInRole(TypeRole.SPECIFICATION_PREDICATE) || methodMatchContext.hasParameterInRole(TypeRole.SPECIFICATION_CONSTRAINT);
    }

}
