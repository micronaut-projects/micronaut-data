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
package io.micronaut.data.model.jpa.criteria.impl.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitable;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.selection.AggregateExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.factory.Projections;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

/**
 * The selection visitor to convert criteria selection to {@link QueryModel}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class QueryModelSelectionVisitor implements SelectionVisitor {

    private final QueryModel queryModel;
    private final boolean distinct;
    private String alias;
    private boolean isCompound;

    public QueryModelSelectionVisitor(QueryModel queryModel, boolean distinct) {
        this.queryModel = queryModel;
        this.distinct = distinct;
    }

    @Override
    public void visit(Predicate predicate) {
        throw new IllegalStateException("Predicate is not allowed as a selection!");
    }

    @Override
    public void visit(PersistentPropertyPath<?> persistentPropertyPath) {
        if (distinct) {
            addProjection(Projections.distinct(persistentPropertyPath.getProperty().getName()));
        } else {
            addProjection(Projections.property(persistentPropertyPath.getPathAsString()));
        }
    }

    @Override
    public void visit(AggregateExpression<?, ?> aggregateExpression) {
        addProjection(getProjection(aggregateExpression));
    }

    private QueryModel.Projection getProjection(AggregateExpression<?, ?> aggregateExpression) {
        Expression<?> expression = aggregateExpression.getExpression();
        switch (aggregateExpression.getType()) {
            case SUM:
                return Projections.sum(CriteriaUtils.requireProperty(expression).getPathAsString());
            case AVG:
                return Projections.avg(CriteriaUtils.requireProperty(expression).getPathAsString());
            case MAX:
                return Projections.max(CriteriaUtils.requireProperty(expression).getPathAsString());
            case MIN:
                return Projections.min(CriteriaUtils.requireProperty(expression).getPathAsString());
            case COUNT:
                if (expression instanceof PersistentEntityRoot) {
                    return Projections.count();
                } else if (expression instanceof PersistentPropertyPath) {
                    // TODO
                    return Projections.count();
                } else {
                    throw new IllegalStateException("Illegal expression: " + expression + " for count selection!");
                }
            case COUNT_DISTINCT:
                if (expression instanceof PersistentEntityRoot) {
                    // TODO
                    return Projections.countDistinct(((PersistentPropertyPath<?>) expression).getPathAsString());
                } else if (expression instanceof PersistentPropertyPath) {
                    return Projections.countDistinct(((PersistentPropertyPath<?>) expression).getPathAsString());
                } else {
                    throw new IllegalStateException("Illegal expression: " + expression + " for count distinct selection!");
                }
            default:
                throw new IllegalStateException("Unknown aggregation: " + aggregateExpression.getExpression());
        }
    }

    @Override
    public void visit(CompoundSelection<?> compoundSelection) {
        isCompound = true;
        for (Selection<?> selection : compoundSelection.getCompoundSelectionItems()) {
            if (selection instanceof SelectionVisitable) {
                ((SelectionVisitable) selection).accept(this);
            } else {
                throw new IllegalStateException("Unknown selection object: " + selection);
            }
        }
        isCompound = false;
    }

    @Override
    public void visit(PersistentEntityRoot<?> entityRoot) {
        if (isCompound) {
            throw new IllegalStateException("Entity root cannot be in compound selection!");
        }
        if (distinct) {
            addProjection(Projections.distinct());
        }
        // default
    }

    @Override
    public void visit(LiteralExpression<?> literalExpression) {
        addProjection(Projections.literal(literalExpression.getValue()));
    }

    @Override
    public void visit(IdExpression<?, ?> idExpression) {
        PersistentEntityRoot<?> root = idExpression.getRoot();
        PersistentEntity persistentEntity = root.getPersistentEntity();
        if (persistentEntity.hasCompositeIdentity()) {
            for (PersistentProperty persistentProperty : persistentEntity.getCompositeIdentity()) {
                if (distinct) {
                    addProjection(Projections.distinct(persistentProperty.getName()));
                } else {
                    addProjection(Projections.property(persistentProperty.getName()));
                }
            }
        } else {
            PersistentProperty identity = persistentEntity.getIdentity();
            if (distinct) {
                addProjection(Projections.distinct(identity.getName()));
            } else {
                addProjection(Projections.property(identity.getName()));
            }
        }
    }

    @Override
    public void visit(AliasedSelection<?> aliasedSelection) {
        alias = aliasedSelection.getAlias();
        ((SelectionVisitable) aliasedSelection.getSelection()).accept(this);
        alias = null;
    }

    private void addProjection(QueryModel.Projection projection) {
        if (projection instanceof QueryModel.PropertyProjection && alias != null) {
            ((QueryModel.PropertyProjection) projection).setAlias(alias);
        }
        queryModel.projections().add(projection);
    }
}
