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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.Optional;

/**
 * JPA specification findOne.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindOneSpecificationMethod extends AbstractSpecificationMethod {
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
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        ClassElement returnType = matchContext.getReturnType();
        if (returnType.isAssignable(Optional.class)) {
            returnType = returnType.getFirstTypeArgument().orElse(returnType);
        }
        return super.isMethodMatch(methodElement, matchContext) && returnType.hasStereotype(MappedEntity.class);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        if (isFirstParameterSpringJpaSpecification(matchContext.getMethodElement())) {
            return new MethodMatchInfo(
                    matchContext.getReturnType(),
                    null,
                    getInterceptorElement(matchContext, "io.micronaut.data.spring.jpa.intercept.FindOneSpecificationInterceptor")
            );
        }
        return new MethodMatchInfo(
                matchContext.getReturnType(),
                null,
                getInterceptorElement(matchContext, "io.micronaut.data.jpa.repository.intercept.FindOneSpecificationInterceptor")
        );
    }

}
