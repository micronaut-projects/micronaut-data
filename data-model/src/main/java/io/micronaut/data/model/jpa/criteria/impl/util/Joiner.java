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
package io.micronaut.data.model.jpa.criteria.impl.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitable;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitable;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExpressionBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInValuesPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyUnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.AggregateExpression;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Traversing the query selection and predicates and extracting required query joins.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class Joiner implements SelectionVisitor, PredicateVisitor {

    private final Map<String, Joined> joins = new TreeMap<>(
            Comparator.comparingInt(String::length).thenComparing(String::compareTo)
    );

    /**
     * Returns required query joins.
     *
     * @return The joins
     */
    public Map<String, Joined> getJoins() {
        return joins;
    }

    /**
     * Join property relationships if needed.
     *
     * @param persistentPropertyPath The property
     */
    public void joinIfNeeded(PersistentPropertyPath<?> persistentPropertyPath) {
        joinIfNeeded(persistentPropertyPath, false);
    }

    private void joinIfNeeded(PersistentPropertyPath<?> persistentPropertyPath, boolean isPredicate) {
        PersistentProperty property = persistentPropertyPath.getProperty();
        if (isPredicate && property instanceof Association) {
            // We don't need a join to access the ID
            return;
        }

        joinAssociation(persistentPropertyPath);
    }

    private void joinAssociation(Path<?> path) {
        if (path instanceof PersistentAssociationPath) {
            PersistentAssociationPath<?, ?> associationPath = (PersistentAssociationPath) path;
            if (associationPath.getAssociation().getKind() == Relation.Kind.EMBEDDED) {
                // Cannot join embedded

                joinAssociation(path.getParentPath());
            } else {
                join(associationPath);
            }
        } else if (path instanceof PersistentPropertyPath persistentPropertyPath) {
            Path parentPath = persistentPropertyPath.getParentPath();
            if (parentPath instanceof PersistentAssociationPath parent) {
                if (parent.getAssociation().getAssociatedEntity().getIdentity() == persistentPropertyPath.getProperty()) {
                    // We don't need a join to access the ID
                    return;
                }
            }
            joinAssociation(parentPath);
        }
    }

    private void join(PersistentAssociationPath<?, ?> associationPath) {
        Joined joined = joins.computeIfAbsent(associationPath.getPathAsString(),
                s -> new Joined(associationPath, associationPath.getAssociationJoinType(), associationPath.getAlias()));

        if (joined.association == associationPath) {
            return;
        }

        Join.Type type = associationPath.getAssociationJoinType();
        if (type != Join.Type.DEFAULT) {
            joined.type = type;
        }
        String alias = associationPath.getAlias();
        if (alias != null) {
            joined.alias = alias;
        }
    }

    @Override
    public void visit(PersistentEntityRoot<?> entityRoot) {
        Set<? extends jakarta.persistence.criteria.Join<?, ?>> joins = entityRoot.getJoins();
        visitJoins(joins);
    }

    private void visitJoins(Set<? extends jakarta.persistence.criteria.Join<?, ?>> joins) {
        for (jakarta.persistence.criteria.Join<?, ?> join : joins) {
            if (join instanceof PersistentAssociationPath persistentAssociationPath) {
                if (persistentAssociationPath.getAssociationJoinType() == null) {
                    continue;
                }
                joinIfNeeded(persistentAssociationPath, false);
                visitJoins(join.getJoins());
            }
        }
    }

    private void visitPredicateExpression(Expression<?> expression) {
        if (expression instanceof PredicateVisitable) {
            ((PredicateVisitable) expression).accept(this);
        } else if (expression instanceof PersistentPropertyPath) {
            joinIfNeeded((PersistentPropertyPath<?>) expression, true);
        }
    }

    private void visitSelectionExpression(Expression<?> expression) {
        if (expression instanceof PersistentPropertyPath) {
            joinIfNeeded((PersistentPropertyPath<?>) expression, false);
        }
    }

    @Override
    public void visit(PersistentPropertyPath<?> persistentPropertyPath) {
        joinIfNeeded(persistentPropertyPath, false);
    }

    @Override
    public void visit(Predicate predicate) {
        // Selection
    }

    @Override
    public void visit(AliasedSelection<?> aliasedSelection) {
        ((SelectionVisitable) aliasedSelection.getSelection()).accept(this);
    }

    @Override
    public void visit(CompoundSelection<?> compoundSelection) {
        for (Selection<?> selection : compoundSelection.getCompoundSelectionItems()) {
            if (selection instanceof SelectionVisitable) {
                ((SelectionVisitable) selection).accept(this);
            } else {
                throw new IllegalStateException("Unknown selection object: " + selection);
            }
        }
    }

    @Override
    public void visit(LiteralExpression<?> literalExpression) {
    }

    @Override
    public void visit(IdExpression<?, ?> idExpression) {
    }

    @Override
    public void visit(AggregateExpression<?, ?> aggregateExpression) {
        visitSelectionExpression(aggregateExpression.getExpression());
    }

    @Override
    public void visit(ConjunctionPredicate conjunction) {
        for (IExpression<Boolean> expression : conjunction.getPredicates()) {
            visitPredicateExpression(expression);
        }
    }

    @Override
    public void visit(DisjunctionPredicate disjunction) {
        for (IExpression<Boolean> expression : disjunction.getPredicates()) {
            visitPredicateExpression(expression);
        }
    }

    @Override
    public void visit(NegatedPredicate negate) {
        visitPredicateExpression(negate.getNegated());
    }

    @Override
    public void visit(PersistentPropertyInPredicate<?> propertyIn) {
        joinIfNeeded(propertyIn.getPropertyPath(), true);
    }

    @Override
    public void visit(PersistentPropertyUnaryPredicate<?> propertyOp) {
        joinIfNeeded(propertyOp.getPropertyPath(), true);
    }

    @Override
    public void visit(PersistentPropertyBetweenPredicate<?> propertyBetweenPredicate) {
        joinIfNeeded(propertyBetweenPredicate.getPropertyPath(), true);
    }

    @Override
    public void visit(PersistentPropertyBinaryPredicate<?> propertyToExpressionOp) {
        joinIfNeeded(propertyToExpressionOp.getPropertyPath(), true);
        visitPredicateExpression(propertyToExpressionOp.getExpression());
    }

    @Override
    public void visit(PersistentPropertyInValuesPredicate<?> inValues) {
        joinIfNeeded(inValues.getPropertyPath(), true);
        inValues.getValues().forEach(this::visitPredicateExpression);
    }

    @Override
    public void visit(ExpressionBinaryPredicate expressionBinaryPredicate) {
        visitPredicateExpression(expressionBinaryPredicate.getLeft());
        visitPredicateExpression(expressionBinaryPredicate.getRight());
    }

    /**
     * The data structure representing a join.
     */
    @Internal
    public static final class Joined {
        private final PersistentAssociationPath<?, ?> association;
        private io.micronaut.data.annotation.Join.Type type;
        private String alias;

        public Joined(PersistentAssociationPath<?, ?> association, io.micronaut.data.annotation.Join.Type type, String alias) {
            this.association = association;
            this.type = type;
            this.alias = alias;
        }

        public PersistentAssociationPath<?, ?> getAssociation() {
            return association;
        }

        public io.micronaut.data.annotation.Join.Type getType() {
            return type;
        }

        public void setType(io.micronaut.data.annotation.Join.Type type) {
            this.type = type;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }
}
