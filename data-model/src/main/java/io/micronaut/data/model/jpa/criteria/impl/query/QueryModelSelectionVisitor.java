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
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.factory.Projections;
import jakarta.persistence.criteria.Expression;
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
    public void visit(PersistentPropertyPath<?> persistentPropertyPath) {
        if (distinct && !hasDistinctProjection()) {
            addProjection(Projections.distinct());
        }
        addProjection(Projections.property(persistentPropertyPath.getPathAsString(), isCompound));
    }

    @Override
    public void visit(UnaryExpression<?> unaryExpression) {
        addProjection(getProjection(unaryExpression));
    }

    private QueryModel.Projection getProjection(UnaryExpression<?> unaryExpression) {
        Expression<?> expression = unaryExpression.getExpression();
        switch (unaryExpression.getType()) {
            case SUM -> {
                return Projections.sum(CriteriaUtils.requireProperty(expression).getPathAsString());
            }
            case AVG -> {
                return Projections.avg(CriteriaUtils.requireProperty(expression).getPathAsString());
            }
            case MAX -> {
                return Projections.max(CriteriaUtils.requireProperty(expression).getPathAsString());
            }
            case MIN -> {
                return Projections.min(CriteriaUtils.requireProperty(expression).getPathAsString());
            }
            case COUNT -> {
                if (expression instanceof PersistentEntityRoot) {
                    return Projections.count();
                } else if (expression instanceof PersistentPropertyPath) {
                    // TODO
                    return Projections.count();
                } else {
                    throw new IllegalStateException("Illegal expression: " + expression + " for count selection!");
                }
            }
            case COUNT_DISTINCT -> {
                if (expression instanceof PersistentEntityRoot) {
                    return Projections.countDistinctRoot();
                } else if (expression instanceof PersistentPropertyPath<?> persistentPropertyPath) {
                    return Projections.countDistinct(persistentPropertyPath.getPathAsString());
                } else {
                    throw new IllegalStateException("Illegal expression: " + expression + " for count distinct selection!");
                }
            }
            default ->
                throw new IllegalStateException("Unknown aggregation: " + unaryExpression.getExpression());
        }
    }

    @Override
    public void visit(CompoundSelection<?> compoundSelection) {
        isCompound = true;
        for (Selection<?> selection : compoundSelection.getCompoundSelectionItems()) {
            if (selection instanceof ISelection<?> selectionVisitable) {
                selectionVisitable.visitSelection(this);
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
        } else {
            addProjection(Projections.rootEntity());
        }
    }

    @Override
    public void visit(PersistentEntitySubquery<?> subquery) {
        throw new IllegalStateException("Subquery is not supported!");
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
                if (distinct && !hasDistinctProjection()) {
                    addProjection(Projections.distinct());
                }
                addProjection(Projections.property(persistentProperty.getName()));
            }
        } else {
            PersistentProperty identity = persistentEntity.getIdentity();
            if (distinct && !hasDistinctProjection()) {
                addProjection(Projections.distinct());
            }
            addProjection(Projections.property(identity.getName()));
        }
    }

    @Override
    public void visit(AliasedSelection<?> aliasedSelection) {
        alias = aliasedSelection.getAlias();
        aliasedSelection.getSelection().visitSelection(this);
        alias = null;
    }

    @Override
    public void visit(FunctionExpression<?> functionExpression) {
        throw new IllegalStateException("Not supported expression: " + functionExpression);
    }

    @Override
    public void visit(BinaryExpression<?> binaryExpression) {
        throw new IllegalStateException("Not supported expression: " + binaryExpression);
    }

    private void addProjection(QueryModel.Projection projection) {
        if (projection instanceof QueryModel.PropertyProjection propertyProjection && alias != null) {
            propertyProjection.setAlias(alias);
        }
        queryModel.projections().add(projection);
    }

    private boolean hasDistinctProjection() {
        return queryModel.getProjections().stream().anyMatch(p -> p instanceof QueryModel.DistinctProjection);
    }
}
