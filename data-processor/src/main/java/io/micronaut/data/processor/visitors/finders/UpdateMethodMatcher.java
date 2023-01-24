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
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.UpdateCriteriaMethodMatch;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Update method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class UpdateMethodMatcher extends AbstractPatternMethodMatcher {

    public UpdateMethodMatcher() {
        super(false, "update", "updateBy");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        MethodElement methodElement = matchContext.getMethodElement();
        ParameterElement[] parameters = methodElement.getParameters();
        ParameterElement idParameter = Arrays.stream(parameters).filter(p -> p.hasAnnotation(Id.class)).findFirst().orElse(null);

        if (parameters.length > 1 && idParameter != null) {
            if (!TypeUtils.isValidBatchUpdateReturnType(methodElement)) {
                throw new MatchFailedException("Update methods only support void or number based return types");
            }
            return batchUpdate(matcher, idParameter);
        }

        final ParameterElement entityParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isEntity(p.getGenericType())).findFirst().orElse(null);
        final ParameterElement entitiesParameter = Arrays.stream(parameters).filter(p -> TypeUtils.isIterableOfEntity(p.getGenericType())).findFirst().orElse(null);
        if ((entityParameter != null || entitiesParameter != null)) {
            return entityUpdate(matcher, entityParameter, entitiesParameter);
        }

        if (!TypeUtils.isValidBatchUpdateReturnType(methodElement)) {
            throw new MatchFailedException("Update methods only support void or number based return types");
        }
        return batchUpdate2(matcher);
    }

    private UpdateCriteriaMethodMatch entityUpdate(java.util.regex.Matcher matcher, ParameterElement entityParameter, ParameterElement entitiesParameter) {
        return new UpdateCriteriaMethodMatch(matcher) {

            final ParameterElement entityParam = entityParameter == null ? entitiesParameter : entityParameter;

            @Override
            protected <T> void applyPredicates(List<ParameterElement> parameters,
                                               PersistentEntityRoot<T> root,
                                               PersistentEntityCriteriaUpdate<T> query,
                                               SourcePersistentEntityCriteriaBuilder cb) {

                final SourcePersistentEntity rootEntity = (SourcePersistentEntity) root.getPersistentEntity();
                Predicate predicate;
                if (rootEntity.getVersion() != null) {
                    predicate = cb.and(
                            cb.equal(root.id(), cb.entityPropertyParameter(entityParam)),
                            cb.equal(root.version(), cb.entityPropertyParameter(entityParam))
                    );
                } else {
                    predicate = cb.equal(root.id(), cb.entityPropertyParameter(entityParam));
                }
                query.where(predicate);
            }

            @Override
            protected <T> void addPropertiesToUpdate(MethodMatchContext matchContext,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {
                final SourcePersistentEntity rootEntity = matchContext.getRootEntity();

                Stream.concat(rootEntity.getPersistentProperties().stream(), Stream.of(rootEntity.getVersion()))
                        .filter(p -> p != null && !((p instanceof Association) && ((Association) p).isForeignKey()) && !p.isGenerated() &&
                                p.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATEABLE, Boolean.class)).orElse(true))
                        .forEach(p -> query.set(p.getName(), cb.entityPropertyParameter(entityParam)));

                if (((AbstractPersistentEntityCriteriaUpdate<T>) query).getUpdateValues().isEmpty()) {
                    // Workaround for only ID entities
                    query.set(rootEntity.getIdentity().getName(), cb.entityPropertyParameter(entityParam));
                }
            }

            @Override
            protected boolean supportedByImplicitQueries() {
                return true;
            }

            @Override
            protected Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveReturnTypeAndInterceptor(MethodMatchContext matchContext) {
                Map.Entry<ClassElement, Class<? extends DataInterceptor>> e = super.resolveReturnTypeAndInterceptor(matchContext);
                ClassElement returnType = e.getKey();
                if (returnType != null
                        && !TypeUtils.isVoid(returnType)
                        && !TypeUtils.isNumber(returnType)
                        && !returnType.hasStereotype(MappedEntity.class)
                        && !(TypeUtils.isReactiveOrFuture(matchContext.getReturnType()) && TypeUtils.isObjectClass(returnType))) {
                    throw new MatchFailedException("Cannot implement update method for specified return type: " + returnType.getName());
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

    private UpdateCriteriaMethodMatch batchUpdate(java.util.regex.Matcher matcher, ParameterElement idParameter) {
        return new UpdateCriteriaMethodMatch(matcher) {

            @Override
            protected <T> void applyPredicates(String querySequence, ParameterElement[] parameters,
                                               PersistentEntityRoot<T> root,
                                               PersistentEntityCriteriaUpdate<T> query,
                                               SourcePersistentEntityCriteriaBuilder cb) {
                super.applyPredicates(querySequence, parameters, root, query, cb);

                ParameterElement versionParameter = Arrays.stream(parameters).filter(p -> p.hasAnnotation(Version.class)).findFirst().orElse(null);
                Predicate predicate;
                if (versionParameter != null) {
                    predicate = cb.and(
                            cb.equal(root.id(), cb.parameter(idParameter)),
                            cb.equal(root.version(), cb.parameter(versionParameter))
                    );
                } else {
                    predicate = cb.equal(root.id(), cb.parameter(idParameter));
                }
                query.where(predicate);
            }

            @Override
            protected <T> void applyPredicates(List<ParameterElement> parameters,
                                               PersistentEntityRoot<T> root,
                                               PersistentEntityCriteriaUpdate<T> query,
                                               SourcePersistentEntityCriteriaBuilder cb) {

                ParameterElement versionParameter = parameters.stream().filter(p -> p.hasAnnotation(Version.class)).findFirst().orElse(null);
                Predicate predicate;
                if (versionParameter != null) {
                    predicate = cb.and(
                            cb.equal(root.id(), cb.parameter(idParameter)),
                            cb.equal(root.version(), cb.parameter(versionParameter))
                    );
                } else {
                    predicate = cb.equal(root.id(), cb.parameter(idParameter));
                }
                query.where(predicate);
            }

            @Override
            protected <T> void addPropertiesToUpdate(MethodMatchContext matchContext,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {

                List<ParameterElement> parameters = matchContext.getParametersNotInRole();
                List<ParameterElement> remainingParameters = parameters.stream()
                        .filter(p -> !p.hasAnnotation(Id.class) && !p.hasAnnotation(Version.class))
                        .collect(Collectors.toList());

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

                for (ParameterElement parameter : remainingParameters) {
                    String name = getParameterName(parameter);
                    SourcePersistentProperty prop = entity.getPropertyByName(name);
                    if (prop == null) {
                        throw new MatchFailedException("Cannot update non-existent property: " + name);
                    } else {
                        if (prop.isGenerated()) {
                            throw new MatchFailedException("Cannot update a generated property: " + name);
                        } else {
                            query.set(name, cb.parameter(parameter));
                        }
                    }
                }
            }

        };
    }

    private UpdateCriteriaMethodMatch batchUpdate2(java.util.regex.Matcher matcher) {
        return new UpdateCriteriaMethodMatch(matcher) {

            @Override
            protected <T> void addPropertiesToUpdate(MethodMatchContext matchContext,
                                                     PersistentEntityRoot<T> root,
                                                     PersistentEntityCriteriaUpdate<T> query,
                                                     SourcePersistentEntityCriteriaBuilder cb) {
                Set<String> queryParameters = query.getParameters()
                        .stream()
                        .map(ParameterExpression::getName)
                        .collect(Collectors.toSet());

                for (ParameterElement p : matchContext.getParametersNotInRole()) {
                    String parameterName = getParameterName(p);
                    if (queryParameters.contains(parameterName)) {
                        continue;
                    }
                    PersistentPropertyPath path = root.getPersistentEntity().getPropertyPath(parameterName);
                    if (path != null) {
                        query.set(path.getProperty().getName(), cb.parameter(p));
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
