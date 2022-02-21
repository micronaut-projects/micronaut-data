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
package io.micronaut.data.processor.visitors.finders.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaBuilder;
import io.micronaut.data.processor.model.criteria.impl.MethodMatchSourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.data.processor.visitors.AnnotationMetadataHierarchy;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractCriteriaMethodMatch;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * Update criteria method match.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public class UpdateCriteriaMethodMatch extends AbstractCriteriaMethodMatch {

    /**
     * Default constructor.
     *
     * @param matcher The matcher
     */
    public UpdateCriteriaMethodMatch(Matcher matcher) {
        super(matcher);
    }

    /**
     * Apply query match.
     *
     * @param matchContext The match context
     * @param root         The root
     * @param query        The query
     * @param cb           The criteria builder
     * @param <T>          The entity type
     */
    protected <T> void apply(MethodMatchContext matchContext,
                             PersistentEntityRoot<T> root,
                             PersistentEntityCriteriaUpdate<T> query,
                             SourcePersistentEntityCriteriaBuilder cb) {
        String querySequence = matcher.group(3);
        if (matcher.group(2).endsWith("By")) {
            applyPredicates(querySequence, matchContext.getParameters(), root, query, cb);
        } else {
            applyPredicates(matchContext.getParametersNotInRole(), root, query, cb);
        }

        if (query.getRestriction() == null) {
            throw new MatchFailedException("Cannot implement batch update operation that doesn't perform a query");
        }

        SourcePersistentEntity entity = matchContext.getRootEntity();

        addPropertiesToUpdate(matchContext, root, query, cb);

        // Add updatable auto-populated parameters
        entity.getPersistentProperties().stream()
                .filter(p -> p != null && p.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATEABLE, Boolean.class)).orElse(false))
                .forEach(p -> query.set(p.getName(), cb.parameter((ParameterElement) null)));

        if (entity.getVersion() != null) {
            if (((AbstractPersistentEntityCriteriaUpdate<T>) query).hasVersionRestriction()) {
                query.set(entity.getVersion().getName(), cb.parameter((ParameterElement) null));
            }
        }

        if (((AbstractPersistentEntityCriteriaUpdate<T>) query).getUpdateValues().isEmpty()) {
            throw new MatchFailedException("At least one parameter required to update");
        }
    }

    protected <T> void addPropertiesToUpdate(MethodMatchContext matchContext,
                                             PersistentEntityRoot<T> root,
                                             PersistentEntityCriteriaUpdate<T> query,
                                             SourcePersistentEntityCriteriaBuilder cb) {
    }

    @Override
    protected MethodMatchInfo build(MethodMatchContext matchContext) {

        MethodMatchSourcePersistentEntityCriteriaBuilderImpl cb = new MethodMatchSourcePersistentEntityCriteriaBuilderImpl(matchContext);

        PersistentEntityCriteriaUpdate<Object> criteriaQuery = cb.createCriteriaUpdate(null);
        apply(matchContext, criteriaQuery.from(matchContext.getRootEntity()), criteriaQuery, cb);

        Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = resolveReturnTypeAndInterceptor(matchContext);
        ClassElement resultType = entry.getKey();
        Class<? extends DataInterceptor> interceptorType = entry.getValue();

        AbstractPersistentEntityCriteriaUpdate<?> criteriaUpdate = (AbstractPersistentEntityCriteriaUpdate<?>) criteriaQuery;
        boolean optimisticLock = criteriaUpdate.hasVersionRestriction();

        final AnnotationMetadataHierarchy annotationMetadataHierarchy = new AnnotationMetadataHierarchy(
                matchContext.getRepositoryClass().getAnnotationMetadata(),
                matchContext.getAnnotationMetadata()
        );
        QueryBuilder queryBuilder = matchContext.getQueryBuilder();

        Map<String, Object> propertiesToUpdate = criteriaUpdate.getUpdateValues();
        QueryModel queryModel = ((AbstractPersistentEntityCriteriaUpdate) criteriaQuery).getQueryModel();
        QueryResult queryResult = queryBuilder.buildUpdate(annotationMetadataHierarchy, queryModel, propertiesToUpdate);

        return new MethodMatchInfo(
                DataMethod.OperationType.UPDATE,
                resultType,
                getInterceptorElement(matchContext, interceptorType)
        )
                .optimisticLock(optimisticLock)
                .queryResult(queryResult);
    }

    @Override
    protected DataMethod.OperationType getOperationType() {
        return DataMethod.OperationType.UPDATE;
    }
}
