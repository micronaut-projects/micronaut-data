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
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import io.micronaut.data.model.jpa.criteria.impl.predicate.AbstractPersistentPropertyPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExpressionBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyUnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateBinaryOp;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.factory.Restrictions;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

/**
 * The predicate visitor to convert criteria predicates to {@link QueryModel}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class QueryModelPredicateVisitor implements PredicateVisitor {

    private final QueryModel queryModel;
    private State state = new State();

    public QueryModelPredicateVisitor(QueryModel queryModel) {
        this.queryModel = queryModel;
    }

    private void visit(IExpression<Boolean> expression) {
        if (expression instanceof IPredicate predicateVisitable) {
            predicateVisitable.visitPredicate(this);
        } else if (expression instanceof PersistentPropertyPath<?> propertyPath) {
            // TODO
            add(Restrictions.isTrue(getPropertyPath(propertyPath)));
        } else {
            throw new IllegalStateException("Unknown boolean expression: " + expression);
        }
    }

    @Override
    public void visit(ConjunctionPredicate conjunction) {
        if (conjunction.getPredicates().isEmpty()) {
            return;
        }
        if (conjunction.getPredicates().size() == 1) {
            visit(conjunction.getPredicates().iterator().next());
            return;
        }
        if (state.junction == null || state.junction instanceof QueryModel.Conjunction) {
            for (IExpression<Boolean> expression : conjunction.getPredicates()) {
                visit(expression);
            }
        } else {
            QueryModel.Conjunction junction = new QueryModel.Conjunction();
            State prevState = pushState();
            this.state.junction = junction;
            for (IExpression<Boolean> expression : conjunction.getPredicates()) {
                visit(expression);
            }
            restoreState(prevState);
            add(junction);
        }
    }

    @Override
    public void visit(DisjunctionPredicate disjunction) {
        if (disjunction.getPredicates().isEmpty()) {
            return;
        }
        if (disjunction.getPredicates().size() == 1) {
            visit(disjunction.getPredicates().iterator().next());
            return;
        }
        QueryModel.Disjunction junction = new QueryModel.Disjunction();
        State prevState = pushState();
        state.junction = junction;
        for (IExpression<Boolean> expression : disjunction.getPredicates()) {
            visit(expression);
        }
        restoreState(prevState);
        add(junction);
    }

    @Override
    public void visit(NegatedPredicate negate) {
        State prevState = pushState();
        this.state.negated = true;
        visit(negate.getNegated());
        restoreState(prevState);
    }

    @Override
    public void visit(PersistentPropertyBinaryPredicate<?> propertyToExpressionOp) {
        PersistentPropertyPath<?> propertyPath = propertyToExpressionOp.getPropertyPath();
        PredicateBinaryOp op = propertyToExpressionOp.getOp();
        Expression<?> expression = propertyToExpressionOp.getExpression();
        visitPropertyPathPredicate(propertyPath, expression, op);
    }

    private void visitPropertyPathPredicate(PersistentPropertyPath<?> propertyPath, Expression<?> expression, PredicateBinaryOp op) {
        if (expression instanceof PersistentPropertyPath<?> persistentPropertyPath) {
            add(getPropertyToPropertyRestriction(op,
                    propertyPath,
                    persistentPropertyPath));
        } else if (expression instanceof ParameterExpression) {
            add(getPropertyToValueRestriction(op,
                    propertyPath,
                    expression));
        } else if (expression instanceof LiteralExpression<?> literalExpression) {
            add(getPropertyToValueRestriction(op,
                    propertyPath,
                    literalExpression.getValue()));
        } else {
            throw new IllegalStateException("Unsupported expression: " + expression);
        }
    }

    @Override
    public void visit(ExpressionBinaryPredicate expressionBinaryPredicate) {
        Expression<?> left = expressionBinaryPredicate.getLeft();
        PredicateBinaryOp op = expressionBinaryPredicate.getOp();
        if (left instanceof PersistentPropertyPath<?> persistentPropertyPath) {
            visitPropertyPathPredicate(persistentPropertyPath,
                    expressionBinaryPredicate.getRight(),
                    op);
        } else if (left instanceof IdExpression) {
            if (op == PredicateBinaryOp.EQUALS) {
                add(Restrictions.idEq(asValue(expressionBinaryPredicate.getRight())));
            } else {
                throw new IllegalStateException("Unsupported ID expression OP: " + op);
            }
        } else {
            throw new IllegalStateException("Unsupported expression: " + left);
        }
    }

    private QueryModel.Criterion getPropertyToValueRestriction(PredicateBinaryOp op,
                                                               PersistentPropertyPath<?> left,
                                                               Object right) {
        String leftProperty = getPropertyPath(left);
        Object rightProperty = asValue(right);

        switch (op) {
            case EQUALS:
                PersistentProperty property = left.getProperty();
                PersistentEntity owner = property.getOwner();
                if (left.getAssociations().isEmpty() && (owner.hasIdentity() && owner.getIdentity() == property)) {
                    return Restrictions.idEq(rightProperty);
                } else if (left.getAssociations().isEmpty() && owner.getVersion() == property) {
                    return Restrictions.versionEq(rightProperty);
                } else {
                    return Restrictions.eq(leftProperty, rightProperty);
                }
            case NOT_EQUALS:
                return Restrictions.ne(leftProperty, rightProperty);
            case GREATER_THAN:
                return Restrictions.gt(leftProperty, rightProperty);
            case GREATER_THAN_OR_EQUALS:
                return Restrictions.gte(leftProperty, rightProperty);
            case LESS_THAN:
                return Restrictions.lt(leftProperty, rightProperty);
            case LESS_THAN_OR_EQUALS:
                return Restrictions.lte(leftProperty, rightProperty);
            case CONTAINS:
                return Restrictions.contains(leftProperty, rightProperty);
            case CONTAINS_IGNORE_CASE:
                return Restrictions.contains(leftProperty, rightProperty).ignoreCase(true);
            case ENDS_WITH:
                return Restrictions.endsWith(leftProperty, rightProperty);
            case STARTS_WITH:
                return Restrictions.startsWith(leftProperty, rightProperty);
            case REGEX:
                return Restrictions.regex(leftProperty, rightProperty);
            case EQUALS_IGNORE_CASE:
                return Restrictions.eq(leftProperty, rightProperty).ignoreCase(true);
            case NOT_EQUALS_IGNORE_CASE:
                return Restrictions.ne(leftProperty, rightProperty).ignoreCase(true);
            case STARTS_WITH_IGNORE_CASE:
                return Restrictions.startsWith(leftProperty, rightProperty).ignoreCase(true);
            case ENDS_WITH_IGNORE_CASE:
                return Restrictions.endsWith(leftProperty, rightProperty).ignoreCase(true);
            case ARRAY_CONTAINS:
                return Restrictions.arrayContains(leftProperty, rightProperty);
            default:
                throw new IllegalStateException("Unsupported property to value operation: " + op);
        }
    }

    private QueryModel.Criterion getPropertyToPropertyRestriction(PredicateBinaryOp op,
                                                                  PersistentPropertyPath<?> left,
                                                                  PersistentPropertyPath<?> right) {
        String leftProperty = getPropertyPath(left);
        String rightProperty = getPropertyPath(right);

        return switch (op) {
            case EQUALS -> Restrictions.eqProperty(leftProperty, rightProperty);
            case NOT_EQUALS -> Restrictions.neProperty(leftProperty, rightProperty);
            case GREATER_THAN -> Restrictions.gtProperty(leftProperty, rightProperty);
            case GREATER_THAN_OR_EQUALS -> Restrictions.geProperty(leftProperty, rightProperty);
            case LESS_THAN -> Restrictions.ltProperty(leftProperty, rightProperty);
            case LESS_THAN_OR_EQUALS -> Restrictions.leProperty(leftProperty, rightProperty);
            default -> throw new IllegalStateException("Unsupported property to property operation: " + op);
        };
    }

    @Override
    public void visit(PersistentPropertyUnaryPredicate<?> propertyOp) {
        String propertyPath = getPropertyPath(propertyOp);
        switch (propertyOp.getOp()) {
            case IS_NULL:
                add(Restrictions.isNull(propertyPath));
                break;
            case IS_NON_NULL:
                add(Restrictions.isNotNull(propertyPath));
                break;
            case IS_TRUE:
                add(Restrictions.isTrue(propertyPath));
                break;
            case IS_FALSE:
                add(Restrictions.isFalse(propertyPath));
                break;
            case IS_EMPTY:
                add(Restrictions.isEmpty(propertyPath));
                break;
            case IS_NOT_EMPTY:
                add(Restrictions.isNotEmpty(propertyPath));
                break;
            default:
                throw new IllegalStateException("Unknown op: " + propertyOp.getOp());
        }
    }

    @Override
    public void visit(PersistentPropertyBetweenPredicate<?> propertyBetweenPredicate) {
        add(Restrictions.between(
                getPropertyPath(propertyBetweenPredicate),
                asValue(propertyBetweenPredicate.getFrom()),
                asValue(propertyBetweenPredicate.getTo())
        ));
    }

    @Override
    public void visit(PersistentPropertyInPredicate<?> inValues) {
        Collection<?> values = inValues.getValues();
        if (!values.isEmpty()) {
            Iterator<?> iterator = values.iterator();
            Object first = iterator.next();
            if (first instanceof ParameterExpression) {
                if (iterator.hasNext()) {
                    throw new IllegalStateException("Only one parameter is supported for IN expression!");
                }
                if (state.negated) {
                    state.negated = false;
                    add(Restrictions.notIn(getPropertyPath(inValues), asValue(first)));
                } else {
                    add(Restrictions.in(getPropertyPath(inValues), asValue(first)));
                }
                return;
            }
        }
        if (state.negated) {
            state.negated = false;
            add(Restrictions.notIn(
                    getPropertyPath(inValues),
                    values.stream().map(this::asValue).toList()
            ));
        } else {
            add(Restrictions.in(
                    getPropertyPath(inValues),
                    values.stream().map(this::asValue).toList()
            ));
        }
    }

    @Override
    public void visit(LikePredicate likePredicate) {
    }

    private Object asValue(Object value) {
        if (value instanceof LiteralExpression<?> literalExpression) {
            return literalExpression.getValue();
        }
        return value;
    }

    private String getPropertyPath(AbstractPersistentPropertyPredicate<?> propertyPredicate) {
        PersistentPropertyPath<?> propertyPath = propertyPredicate.getPropertyPath();
        return getPropertyPath(propertyPath);
    }

    private String getPropertyPath(PersistentPropertyPath<?> propertyPath) {
        return asPath(propertyPath.getAssociations(), propertyPath.getProperty());
    }

    private String asPath(List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return property.getName();
        }
        StringJoiner joiner = new StringJoiner(".");
        for (Association association : associations) {
            joiner.add(association.getName());
        }
        joiner.add(property.getName());
        return joiner.toString();
    }

    private void add(QueryModel.Criterion criterion) {
        if (state.negated) {
            QueryModel.Negation negation = new QueryModel.Negation();
            negation.add(criterion);
            criterion = negation;
        }
        if (state.junction == null) {
            queryModel.add(criterion);
        } else {
            state.junction.add(criterion);
        }
    }

    private State pushState() {
        State prevState = this.state;
        State newState = new State();
        newState.junction = prevState.junction;
        newState.negated = prevState.negated;
        this.state = newState;
        return prevState;
    }

    private State restoreState(State state) {
        State oldState = this.state;
        this.state = state;
        return oldState;
    }

    private static final class State {
        boolean negated;
        QueryModel.Junction junction;
    }
}
