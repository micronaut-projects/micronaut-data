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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractListMethod;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Optional;

/**
 * Spring Data JPA specification findOne.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindOneSpecificationMethod extends AbstractListMethod {
    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"get", "find", "search", "query"};

    /**
     * Find one method.
     */
    public FindOneSpecificationMethod() {
        super(PREFIXES);
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 200;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {

        final VisitorContext visitorContext = matchContext.getVisitorContext();
        ClassElement returnType = matchContext.getReturnType();
        if (returnType.isAssignable(Optional.class)) {
            returnType = returnType.getFirstTypeArgument().orElse(returnType);
        }

        return super.isMethodMatch(methodElement, matchContext) &&
                returnType.hasStereotype(MappedEntity.class) &&
                visitorContext.getClassElement("io.micronaut.data.spring.jpa.intercept.FindOneSpecificationInterceptor").isPresent() &&
                visitorContext.getClassElement("org.springframework.data.jpa.domain.Specification").isPresent() &&
                isFirstParameterJpaSpecification(methodElement);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        final ClassElement interceptorElement = getInterceptorElement(matchContext,
                "io.micronaut.data.spring.jpa.intercept.FindOneSpecificationInterceptor");
        return new MethodMatchInfo(
                matchContext.getReturnType(),
                null,
                interceptorElement
        );
    }

    private boolean isFirstParameterJpaSpecification(@NonNull MethodElement methodElement) {
        final ParameterElement[] parameters = methodElement.getParameters();
        return parameters.length == 1 &&
                parameters[0].getType().isAssignable("org.springframework.data.jpa.domain.Specification");
    }
}
