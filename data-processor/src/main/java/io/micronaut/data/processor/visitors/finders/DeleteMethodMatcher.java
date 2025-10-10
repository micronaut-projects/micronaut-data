/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.DeleteCriteriaMethodMatch;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Count method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class DeleteMethodMatcher extends AbstractMethodMatcher {

    public static final Pattern METHOD_PATTERN = Pattern.compile("^((delete|remove|erase|eliminate)(\\S*?))$");

    /**
     * Default constructor.
     */
    public DeleteMethodMatcher() {
        super(MethodNameParser.builder()
            .match(QueryMatchId.PREFIX, "delete", "remove", "erase", "eliminate")
            .tryMatch(QueryMatchId.ALL_OR_ONE, ALL_OR_ONE)
            .tryMatchLastOccurrencePrefixed(QueryMatchId.RETURNING, null, RETURNING)
            .tryMatchFirstOccurrencePrefixed(QueryMatchId.PREDICATE, BY)
            .failOnRest("Delete method doesn't support projections")
            .build());
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        ParameterElement[] parameters = matchContext.getParameters();
        boolean isSpecificDelete = matches.stream().anyMatch(m -> m.id() == QueryMatchId.PREDICATE);
        boolean isReturning = matches.stream().anyMatch(m -> m.id() == QueryMatchId.RETURNING);
        ParameterElement entityParameter = null;
        ParameterElement entitiesParameter = null;
        if (matchContext.getParametersNotInRole().size() == 1) {
            entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
            entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
        }
        if (isSpecificDelete) {
            // Un-mark the entity parameter if there is a property named the same and 'By' syntax is used
            if (entityParameter != null) {
                if (matchContext.getRootEntity().getPropertyByName(getName(entityParameter)) != null) {
                    entityParameter = null;
                }
            }
            if (entitiesParameter != null) {
                if (matchContext.getRootEntity().getPropertyByName(getName(entitiesParameter)) != null) {
                    entitiesParameter = null;
                }
            }
        }
        if (entityParameter == null && entitiesParameter == null) {
            if (!isReturning && !TypeUtils.isValidBatchUpdateReturnType(matchContext.getMethodElement())) {
                return null;
            }
            return new DeleteCriteriaMethodMatch(matches, isReturning);
        }

        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        if (!rootEntity.hasIdentity() && !rootEntity.hasCompositeIdentity()) {
            throw new MatchFailedException("Delete all not supported for entities with no ID");
        }

        boolean supportedByImplicitQueries = !isSpecificDelete;

        ParameterElement finalEntityParameter = entityParameter;
        ParameterElement finalEntitiesParameter = entitiesParameter;

        return new DeleteCriteriaMethodMatch(matches, isReturning) {

            @Override
            protected boolean supportedByImplicitQueries() {
                return supportedByImplicitQueries;
            }

            @Override
            protected ParameterElement getEntityParameter() {
                return finalEntityParameter;
            }

            @Override
            protected ParameterElement getEntitiesParameter() {
                return finalEntitiesParameter;
            }

        };
    }

    private String getName(ParameterElement entityParameter) {
        return entityParameter.stringValue(Parameter.class).orElseGet(entityParameter::getName);
    }
}
