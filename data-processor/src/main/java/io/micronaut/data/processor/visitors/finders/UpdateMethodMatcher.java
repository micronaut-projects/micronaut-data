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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.UpdateCriteriaMethodMatch;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Update method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class UpdateMethodMatcher extends AbstractMethodMatcher {

    public UpdateMethodMatcher() {
        super(MethodNameParser.builder()
            .match(QueryMatchId.PREFIX, "update", "modify")
            .tryMatch(QueryMatchId.ALL_OR_ONE, ALL_OR_ONE)
            .tryMatchLastOccurrencePrefixed(QueryMatchId.RETURNING, null, RETURNING)
            .tryMatchFirstOccurrencePrefixed(QueryMatchId.PREDICATE, BY)
            .build());
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, List<MethodNameParser.Match> matches) {
        MethodElement methodElement = matchContext.getMethodElement();
        ParameterElement[] parameters = methodElement.getParameters();

        boolean isReturning = matches.stream().anyMatch(m -> m.id() == QueryMatchId.RETURNING);
        if (parameters.length > 1) {
            ParameterElement idParameter = Arrays.stream(parameters).filter(p -> p.hasAnnotation(Id.class)).findFirst().orElse(null);
            if (idParameter != null) {
                if (!isReturning && !TypeUtils.isValidBatchUpdateReturnType(methodElement)) {
                    throw new MatchFailedException("Update methods only support void or number based return types");
                }
                return batchUpdate(matches, isReturning);
            }
        }

        final ParameterElement entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
        final ParameterElement entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);

        if (entityParameter != null || entitiesParameter != null) {
            return entityUpdate(matches, entityParameter, entitiesParameter, isReturning);
        }

        if (!isReturning && !TypeUtils.isValidBatchUpdateReturnType(methodElement)) {
            throw new MatchFailedException("Update methods only support void or number based return types");
        }
        return batchUpdateBy(matches, isReturning);
    }

    public static UpdateCriteriaMethodMatch entityUpdate(List<MethodNameParser.Match> matches,
                                                   ParameterElement entityParameter,
                                                   ParameterElement entitiesParameter,
                                                   boolean isReturning) {
        return new UpdateCriteriaMethodMatch(matches, isReturning) {

            final ParameterElement entityParam = entityParameter == null ? entitiesParameter : entityParameter;

            protected <T> void addPropertiesToUpdate(List<ParameterElement> nonConsumedParameters,
                                                     MethodMatchContext matchContext,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {
                final SourcePersistentEntity rootEntity = matchContext.getRootEntity();

                // for JSON entity representation we don't update all entity fields but all fields at once via JSON update
                if (DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(matchContext.getAnnotationMetadata())) {
                    AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = rootEntity.getAnnotationMetadata().getAnnotation(EntityRepresentation.class);
                    String columnName = entityRepresentationAnnotationValue.getRequiredValue("column", String.class);
                    query.set(columnName, cb.parameter(entityParameter, null));
                    return;
                }

                Stream.concat(rootEntity.getPersistentProperties().stream(), Stream.of(rootEntity.getVersion()))
                        .filter(p -> p != null && !(p instanceof Association association && association.isForeignKey()) && !p.isGenerated() &&
                                p.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATEABLE, Boolean.class)).orElse(true))
                        .forEach(p -> query.set(p.getName(), cb.entityPropertyParameter(entityParam, new PersistentPropertyPath(p))));

                if (((AbstractPersistentEntityCriteriaUpdate<T>) query).getUpdateValues().isEmpty()) {
                    // Workaround for only ID entities
                    query.set(rootEntity.getIdentity().getName(), cb.entityPropertyParameter(entityParam, new PersistentPropertyPath(rootEntity.getIdentity())));
                }
            }

            @Override
            protected boolean supportedByImplicitQueries() {
                return true;
            }

            @Override
            protected FindersUtils.InterceptorMatch resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
                MethodElement methodElement = matchContext.getMethodElement();
                FindersUtils.InterceptorMatch e = super.resolveReturnTypeAndInterceptor(matchContext);
                ClassElement returnType = e.returnType();
                if (!isReturning && returnType != null
                        && !TypeUtils.isVoid(returnType)
                        && !TypeUtils.isNumber(returnType)
                        && !returnType.hasStereotype(MappedEntity.class)
                        && !(TypeUtils.isReactiveOrFuture(matchContext.getReturnType()) && TypeUtils.isObjectClass(returnType))) {
                    throw new MatchFailedException("Cannot implement update method for specified return type: " + returnType.getName() + " " + methodElement.getReturnType() + " " + methodElement.getDescription(false));
                }
                return e;
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

    private UpdateCriteriaMethodMatch batchUpdate(List<MethodNameParser.Match> matches, boolean isReturning) {
        return new UpdateCriteriaMethodMatch(matches, isReturning) {

            @Override
            protected <T> void addPropertiesToUpdate(List<ParameterElement> nonConsumedParameters,
                                                     MethodMatchContext matchContext,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {

                List<ParameterElement> parameters = matchContext.getParametersNotInRole();

                ParameterElement idParameter = parameters.stream().filter(p -> p.hasAnnotation(Id.class)).findFirst()
                        .orElse(null);
                if (idParameter == null) {
                    throw new MatchFailedException("ID required for update method, but not specified");
                }
                SourcePersistentEntity entity = (SourcePersistentEntity) root.getPersistentEntity();
                // Validate @IdClass for composite entity
                if (entity.hasIdentity()) {
                    SourcePersistentProperty identity = entity.getIdentity();
                    String idType = TypeUtils.getTypeName(identity.getType());
                    String idParameterType = TypeUtils.getTypeName(idParameter.getType());
                    if (!idType.equals(idParameterType)) {
                        throw new MatchFailedException("ID type of method [" + idParameterType + "] does not match ID type of entity: " + idType);
                    }
                } else {
                    throw new MatchFailedException("Cannot update by ID for entity that has no ID");
                }

                for (ParameterElement parameter : nonConsumedParameters) {
                    String name = getParameterName(parameter);
                    SourcePersistentProperty prop = entity.getPropertyByName(name);
                    if (prop == null) {
                        throw new MatchFailedException("Cannot update non-existent property: " + name);
                    } else {
                        if (prop.isGenerated()) {
                            throw new MatchFailedException("Cannot update a generated property: " + name);
                        } else {
                            query.set(name, cb.parameter(parameter, new PersistentPropertyPath(prop)));
                        }
                    }
                }
            }

        };
    }

    private UpdateCriteriaMethodMatch batchUpdateBy(List<MethodNameParser.Match> matches,
                                                    boolean isReturning) {
        return new UpdateCriteriaMethodMatch(matches, isReturning) {

            @Override
            protected <T> void addPropertiesToUpdate(List<ParameterElement> nonConsumedParameters,
                                                     MethodMatchContext matchContext,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {

                for (ParameterElement p : nonConsumedParameters) {
                    String parameterName = getParameterName(p);
                    PersistentEntity persistentEntity = root.getPersistentEntity();
                    PersistentPropertyPath path = persistentEntity.getPropertyPath(persistentEntity.getPath(parameterName).orElse(parameterName));
                    if (path != null) {
                        PersistentProperty property = path.getProperty();
                        if (path.getAssociations().isEmpty()) {
                            query.set(property.getName(), cb.parameter(p, path));
                        } else {
                            // TODO: support embedded ID
                            Association association = path.getAssociations().get(0);
                            if (path.getAssociations().size() == 1 && PersistentEntityUtils.isAccessibleWithoutJoin(association, property)) {
                                // Added Void type to satisfy the type check
                                Path<Void> pp = root.join(association.getName()).get(property.getName());
                                Expression<Void> parameter = cb.parameter(p, path);
                                query.set(pp, parameter);
                            } else {
                                throw new MatchFailedException("Cannot perform batch update for a property with an association: " + parameterName);
                            }
                        }
                    } else {
                        throw new MatchFailedException("Cannot perform batch update for non-existent property: " + parameterName);
                    }
                }
            }

        };
    }

    private String getParameterName(ParameterElement p) {
        return p.stringValue(Parameter.class).orElse(p.getName());
    }

}
