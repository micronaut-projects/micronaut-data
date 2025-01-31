/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.processor.visitors.finders.annotated;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Update;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.data.processor.visitors.finders.UpdateMethodMatcher;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.ProcessingException;

import java.util.Arrays;
import java.util.Collections;

/**
 * The Update annotation matcher.
 *
 * @author Denis Stepanov
 * @since 4.12
 */
@Internal
public final class UpdateAnnotatedMethodMatcher implements MethodMatcher {

    @Override
    public MethodMatch match(MethodMatchContext matchContext) {
        if (matchContext.getMethodElement().hasStereotype(Update.class)) {
            if (matchContext.getRootEntity() == null) {
                throw new ProcessingException(matchContext.getMethodElement(), "Repository does not have a well-defined primary entity type");
            }
            ParameterElement[] parameters = matchContext.getParameters();
            final ParameterElement entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
            final ParameterElement entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);

            if (entityParameter != null || entitiesParameter != null) {
                return UpdateMethodMatcher.entityUpdate(Collections.emptyList(), entityParameter, entitiesParameter, false);
            }
            throw new ProcessingException(matchContext.getMethodElement(), "Update method should include an entity to update");
        }
        return null;
    }

    @Override
    public int getOrder() {
        return MethodMatcher.DEFAULT_POSITION - 3000;
    }
}
