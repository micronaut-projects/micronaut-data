/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelPredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.query.QueryModelSelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.util.Joiner;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Order;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * The Legacy query builder wrapper.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
final class LegacyQueryModelQueryBuilder implements QueryBuilder2 {

    private final QueryBuilder queryBuilder;

    LegacyQueryModelQueryBuilder(QueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    @NonNull
    private QueryModelPredicateVisitor createPredicateVisitor(QueryModel queryModel) {
        return new QueryModelPredicateVisitor(queryModel);
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public QueryResult buildSelect(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition) {
        QueryModel qm = QueryModel.from(definition.persistentEntity());
        Joiner joiner = new Joiner();
        if (definition.predicate() instanceof IPredicate predicateVisitable) {
            predicateVisitable.visitPredicate(createPredicateVisitor(qm));
            predicateVisitable.visitPredicate(joiner);
        }
        PersistentEntityRoot<?> entityRoot = (PersistentEntityRoot<?>) definition.root();
        if (definition.selection() instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(new QueryModelSelectionVisitor(qm, definition.isDistinct()));
            selectionVisitable.visitSelection(joiner);
            entityRoot.visitSelection(joiner);
        } else {
            entityRoot.visitSelection(new QueryModelSelectionVisitor(qm, definition.isDistinct()));
            entityRoot.visitSelection(joiner);
        }
        List<Order> orders = definition.order();
        if (orders != null && !orders.isEmpty()) {
            List<Sort.Order> sortOrders = orders.stream().map(o -> {
                PersistentPropertyPath<?> propertyPath = requireProperty(o.getExpression());
                joiner.joinIfNeeded(propertyPath);
                String name = propertyPath.getPathAsString();
                if (o.isAscending()) {
                    return Sort.Order.asc(name);
                }
                return Sort.Order.desc(name);
            }).toList();
            qm.sort(Sort.of(sortOrders));
        }
        for (Map.Entry<String, Joiner.Joined> e : joiner.getJoins().entrySet()) {
            qm.join(e.getKey(), Optional.ofNullable(e.getValue().getType()).orElse(Join.Type.DEFAULT), e.getValue().getAlias());
        }

        qm.max(definition.limit());
        qm.offset(definition.offset());
        if (definition.isForUpdate()) {
            qm.forUpdate();
        }
        return queryBuilder.buildQuery(annotationMetadata, qm);
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, UpdateQueryDefinition definition) {
        QueryModel qm = QueryModel.from(definition.persistentEntity());
        Joiner joiner = new Joiner();
        if (definition.predicate() instanceof IPredicate predicateVisitable) {
            predicateVisitable.visitPredicate(createPredicateVisitor(qm));
            predicateVisitable.visitPredicate(joiner);
        }
        if (definition.returningSelection() instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(new QueryModelSelectionVisitor(qm, false));
            selectionVisitable.visitSelection(joiner);
        }
        for (Map.Entry<String, Joiner.Joined> e : joiner.getJoins().entrySet()) {
            qm.join(e.getKey(), Optional.ofNullable(e.getValue().getType()).orElse(Join.Type.DEFAULT), e.getValue().getAlias());
        }
        return queryBuilder.buildUpdate(qm, definition.propertiesToUpdate());
    }

    @Override
    public QueryResult buildDelete(AnnotationMetadata annotationMetadata, DeleteQueryDefinition definition) {
        if (definition.persistentEntity() == null) {
            throw new IllegalStateException("The root entity must be specified!");
        }
        QueryModel qm = QueryModel.from(definition.persistentEntity());
        Joiner joiner = new Joiner();
        if (definition.predicate() instanceof IPredicate predicateVisitable) {
            predicateVisitable.visitPredicate(createPredicateVisitor(qm));
            predicateVisitable.visitPredicate(joiner);
        }
        if (definition.returningSelection() instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(new QueryModelSelectionVisitor(qm, false));
            selectionVisitable.visitSelection(joiner);
        }
        for (Map.Entry<String, Joiner.Joined> e : joiner.getJoins().entrySet()) {
            qm.join(e.getKey(), Optional.ofNullable(e.getValue().getType()).orElse(Join.Type.DEFAULT), e.getValue().getAlias());
        }
        return queryBuilder.buildDelete(qm);
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        throw new IllegalStateException("Not supported");
    }

}
