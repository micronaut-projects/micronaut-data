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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.DeleteCriteriaMethodMatch;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.Predicate;

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
public final class DeleteMethodMatcher extends AbstractPatternMethodMatcher {

    public static final Pattern METHOD_PATTERN = Pattern.compile("^((delete|remove|erase|eliminate)(\\S*?))$");

    /**
     * Default constructor.
     */
    public DeleteMethodMatcher() {
        super(false, "delete", "remove", "erase", "eliminate", "deleteAll", "removeAll", "eraseAll", "eliminateAll");
    }

    @Override
    public MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        ParameterElement[] parameters = matchContext.getParameters();
        final ParameterElement entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
        final ParameterElement entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
        if (entityParameter == null && entitiesParameter == null) {
            if (!TypeUtils.isValidBatchUpdateReturnType(matchContext.getMethodElement())) {
                return null;
            }
            return new DeleteCriteriaMethodMatch(matcher);
        }

        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        if (!rootEntity.hasIdentity() && !rootEntity.hasCompositeIdentity()) {
            throw new MatchFailedException("Delete all not supported for entities with no ID");
        }

        boolean supportedByImplicitQueries = !matcher.group(2).endsWith("By");

        final String idName;
        final SourcePersistentProperty identity = rootEntity.getIdentity();
        if (identity != null) {
            idName = identity.getName();
        } else {
            idName = TypeRole.ID;
        }

        boolean generateInIdList = entitiesParameter != null
                && !rootEntity.hasCompositeIdentity()
                && !(rootEntity.getIdentity() instanceof Embedded) && rootEntity.getVersion() == null;
        if (generateInIdList) {
            return new DeleteCriteriaMethodMatch(matcher) {

                @Override
                protected boolean supportedByImplicitQueries() {
                    return supportedByImplicitQueries;
                }

                @Override
                protected <T> void applyPredicates(List<ParameterElement> parameters,
                                                   PersistentEntityRoot<T> root,
                                                   PersistentEntityCriteriaDelete<T> query,
                                                   SourcePersistentEntityCriteriaBuilder cb) {
                    Predicate restriction = query.getRestriction();
                    Predicate predicate = root.id().in(cb.entityPropertyParameter(entitiesParameter));
                    if (restriction == null) {
                        query.where(predicate);
                    } else {
                        query.where(cb.and(predicate, restriction));
                    }
                }

                @Override
                protected ParameterElement getEntityParameter() {
                    return entityParameter;
                }

                @Override
                protected ParameterElement getEntitiesParameter() {
                    return entitiesParameter;
                }

            };
        }

        ParameterElement entityParam = entityParameter == null ? entitiesParameter : entityParameter;

        return new DeleteCriteriaMethodMatch(matcher) {

            @Override
            protected boolean supportedByImplicitQueries() {
                return supportedByImplicitQueries;
            }

            @Override
            protected <T> void applyPredicates(List<ParameterElement> parameters,
                                               PersistentEntityRoot<T> root,
                                               PersistentEntityCriteriaDelete<T> query,
                                               SourcePersistentEntityCriteriaBuilder cb) {
                Predicate restriction = query.getRestriction();
                Predicate predicate;
                if (rootEntity.getVersion() != null) {
                    predicate = cb.and(
                            cb.equal(root.id(), cb.entityPropertyParameter(entityParam)),
                            cb.equal(root.version(), cb.entityPropertyParameter(entityParam))
                    );
                } else {
                    predicate = cb.equal(root.id(), cb.entityPropertyParameter(entityParam));
                }
                if (restriction == null) {
                    query.where(predicate);
                } else {
                    query.where(cb.and(predicate, restriction));
                }
            }

            @Override
            protected ParameterElement getEntityParameter() {
                return entityParameter;
            }

            @Override
            protected ParameterElement getEntitiesParameter() {
                return entitiesParameter;
            }

        };
    }
}
