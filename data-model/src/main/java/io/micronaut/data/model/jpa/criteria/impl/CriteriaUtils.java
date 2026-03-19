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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.SubqueryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.CoalesceExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.SearchedCaseExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.SimpleCaseExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BetweenPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.UnaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Criteria util class.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class CriteriaUtils {

    private CriteriaUtils() {
    }

    public static boolean isNumeric(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return Number.class.isAssignableFrom(ReflectionUtils.getPrimitiveType(clazz));
        }
        return Number.class.isAssignableFrom(clazz);
    }

    public static boolean isBoolean(Class<?> clazz) {
        return Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz);
    }

    public static boolean isComparable(Class<?> clazz) {
        return Comparable.class.isAssignableFrom(clazz) || isNumeric(clazz) ;
    }

    public static boolean isTextual(Class<?> clazz) {
        return CharSequence.class.isAssignableFrom(clazz);
    }

    public static List<IExpression<Boolean>> requireBoolExpressions(Iterable<? extends Expression<?>> restrictions) {
        return CollectionUtils.iterableToList(restrictions).stream().map(CriteriaUtils::requireBoolExpression).toList();
    }

    public static <T> IExpression<T> requireIExpression(Expression<T> exp) {
        if (exp instanceof IExpression<T> expression) {
            return expression;
        }
        throw new IllegalStateException("Expected an IExpression! Got: " + exp);
    }

    public static <T> PersistentEntitySubquery<T> requirePersistentEntitySubquery(Subquery<T> subquery) {
        if (subquery instanceof PersistentEntitySubquery<T> persistentEntitySubquery) {
            return persistentEntitySubquery;
        }
        throw new IllegalStateException("Expected an PersistentEntitySubquery! Got: " + subquery);
    }

    public static IExpression<String> requireNumericExpression(Expression<?> exp) {
        IExpression expression = requireIExpression(exp);
        if (expression instanceof ParameterExpression ||  expression.getExpressionType().isNumeric()) {
            return expression;
        }
        throw new IllegalStateException("Expected a numeric expression! Got: " + expression.getExpressionType().getName());
    }

    public static IExpression<String> requireStringExpression(Expression<?> exp) {
        IExpression expression = requireIExpression(exp);
        if (expression instanceof ParameterExpression || expression.getExpressionType().isTextual()) {
            return expression;
        }
        throw new IllegalStateException("Expected a string expression! Got: " + expression.getExpressionType().getName());
    }

    public static <T> Expression<T> requireComparableExpression(Expression<T> exp) {
        IExpression expression = requireIExpression(exp);
        if (expression instanceof ParameterExpression || expression.getExpressionType().isComparable()) {
            return expression;
        }
        throw new IllegalStateException("Expected a comparable expression! Got: " + expression.getExpressionType().getName());
    }

    public static IExpression<Boolean> requireBoolExpression(Expression<?> exp) {
        IExpression expression = requireIExpression(exp);
        if (expression instanceof ParameterExpression ||  expression.getExpressionType().isBoolean()) {
            return expression;
        }
        throw new IllegalStateException("Expected a boolean expression! Got: " + expression.getExpressionType().getName());
    }

    public static <T> ParameterExpression<T> requireParameter(Expression<T> exp) {
        if (exp instanceof ParameterExpression<T> parameterExpression) {
            return parameterExpression;
        }
        throw new IllegalStateException("Expression is expected to be a parameter! Got: " + exp);
    }

    public static <T> PersistentPropertyPath<T> requireProperty(Expression<? extends T> exp) {
        if (exp instanceof PersistentPropertyPath persistentPropertyPath) {
            return persistentPropertyPath;
        }
        throw new IllegalStateException("Expression is expected to be a property path! Got: " + exp);
    }

    public static <T> IExpression<T> requirePropertyOrRoot(Expression<T> exp) {
        if (exp instanceof PersistentPropertyPath || exp instanceof PersistentEntityRoot) {
            return (IExpression<T>) exp;
        }
        throw new IllegalStateException("Expression is expected to be a property path or a root! Got: " + exp);
    }

    public static IllegalStateException notSupportedOperation() {
        return new IllegalStateException("Not supported operation!");
    }

    public static boolean hasVersionPredicate(Expression<?> predicate) {
        if (predicate instanceof BinaryPredicate binaryPredicate) {
            if (binaryPredicate.getLeftExpression() instanceof PersistentPropertyPath<?> pp &&
                pp.getProperty().getOwner().hasVersion() && pp.getProperty() == pp.getProperty().getOwner().getVersion()) {
                return true;
            }
            if (binaryPredicate.getRightExpression() instanceof PersistentPropertyPath<?> pp &&
                pp.getProperty().getOwner().hasVersion() && pp.getProperty() == pp.getProperty().getOwner().getVersion()) {
                return true;
            }
        }

        if (predicate instanceof ConjunctionPredicate conjunctionPredicate) {
            for (IExpression<Boolean> pred : conjunctionPredicate.getPredicates()) {
                if (hasVersionPredicate(pred)) {
                    return true;
                }
            }
        }
        if (predicate instanceof DisjunctionPredicate disjunctionPredicate) {
            for (IExpression<Boolean> pred : disjunctionPredicate.getPredicates()) {
                if (hasVersionPredicate(pred)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Set<ParameterExpression<?>> extractPredicateParameters(@Nullable Expression<?> predicate) {
        if (predicate == null) {
            return Collections.emptySet();
        }
        Set<ParameterExpression<?>> properties = new LinkedHashSet<>();
        extractPredicateParameters(predicate, properties);
        return properties;
    }

    public static Set<ParameterExpression<?>> extractParameters(@Nullable Expression<?> predicate,
                                                                @Nullable Selection<?> selection,
                                                                @Nullable List<Order> orders) {
        ParameterCollector parameterCollector = new ParameterCollector();
        if (predicate != null) {
            parameterCollector.collect(predicate);
        }
        if (selection instanceof ISelection<?> iSelection) {
            iSelection.visitSelection(parameterCollector);
        }
        if (orders != null) {
            for (Order order : orders) {
                parameterCollector.collect(order.getExpression());
            }
        }
        return parameterCollector.parameters();
    }

    private static void extractPredicateParameters(Expression<?> predicate, Set<ParameterExpression<?>> parameters) {
        switch (predicate) {
            case LiteralExpression<?> ignored -> {
            }
            case BinaryPredicate binaryPredicate -> {
                if (binaryPredicate.getLeftExpression() instanceof ParameterExpression<?> parameterExpression) {
                    parameters.add(parameterExpression);
                }
                if (binaryPredicate.getRightExpression() instanceof ParameterExpression<?> parameterExpression) {
                    parameters.add(parameterExpression);
                }
            }
            case InPredicate<?> pp -> {
                for (Expression<?> expression : pp.getValues()) {
                    if (expression instanceof ParameterExpression<?> parameterExpression) {
                        parameters.add(parameterExpression);
                    }
                }
            }
            case ConjunctionPredicate conjunctionPredicate -> {
                for (IExpression<Boolean> pred : conjunctionPredicate.getPredicates()) {
                    extractPredicateParameters(pred, parameters);
                }
            }
            case DisjunctionPredicate disjunctionPredicate -> {
                for (IExpression<Boolean> pred : disjunctionPredicate.getPredicates()) {
                    extractPredicateParameters(pred, parameters);
                }
            }
            default ->
                throw new IllegalStateException("Unsupported predicate type: " + predicate.getClass().getSimpleName());
        }
    }

    private static final class ParameterCollector implements PredicateVisitor, SelectionVisitor {

        private final Set<ParameterExpression<?>> parameters = new LinkedHashSet<>();

        Set<ParameterExpression<?>> parameters() {
            return parameters;
        }

        void collect(Expression<?> expression) {
            if (expression instanceof IParameterExpression<?> parameterExpression) {
                visit(parameterExpression);
            } else if (expression instanceof IPredicate predicate) {
                predicate.visitPredicate(this);
            } else if (expression instanceof ISelection<?> selection) {
                selection.visitSelection(this);
            }
        }

        private void collectAll(Collection<? extends Expression<?>> expressions) {
            for (Expression<?> expression : expressions) {
                collect(expression);
            }
        }

        @Override
        public void visit(Predicate predicate) {
            if (predicate instanceof IPredicate iPredicate) {
                iPredicate.visitPredicate(this);
            }
        }

        @Override
        public void visit(PersistentPropertyPath<?> persistentPropertyPath) {
        }

        @Override
        public void visit(PersistentEntityRoot<?> entityRoot) {
        }

        @Override
        public void visit(PersistentEntitySubquery<?> subquery) {
            parameters.addAll(subquery.getParameters());
        }

        @Override
        public void visit(LiteralExpression<?> literalExpression) {
        }

        @Override
        public void visit(UnaryExpression<?> unaryExpression) {
            collect(unaryExpression.getExpression());
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            collect(binaryExpression.getLeft());
            collect(binaryExpression.getRight());
        }

        @Override
        public void visit(IdExpression<?, ?> idExpression) {
        }

        @Override
        public void visit(FunctionExpression<?> functionExpression) {
            collectAll(functionExpression.getExpressions());
        }

        @Override
        public void visit(CoalesceExpression<?> coalesceExpression) {
            for (Expression<?> value : coalesceExpression.getValues()) {
                collect(value);
            }
        }

        @Override
        public void visit(SearchedCaseExpression<?> searchedCaseExpression) {
            for (SearchedCaseExpression.WhenClause<?> whenClause : searchedCaseExpression.getWhenClauses()) {
                collect(whenClause.condition());
                collect(whenClause.result());
            }
            if (searchedCaseExpression.getOtherwise() != null) {
                collect(searchedCaseExpression.getOtherwise());
            }
        }

        @Override
        public void visit(SimpleCaseExpression<?, ?> simpleCaseExpression) {
            collect(simpleCaseExpression.getExpression());
            for (SimpleCaseExpression.WhenClause<?, ?> whenClause : simpleCaseExpression.getWhenClauses()) {
                collect(whenClause.condition());
                collect(whenClause.result());
            }
            if (simpleCaseExpression.getOtherwise() != null) {
                collect(simpleCaseExpression.getOtherwise());
            }
        }

        @Override
        public void visit(IParameterExpression<?> parameterExpression) {
            parameters.add(parameterExpression);
        }

        @Override
        public void visit(SubqueryExpression<?> subqueryExpression) {
            parameters.addAll(subqueryExpression.getSubquery().getParameters());
        }

        @Override
        public void visit(AliasedSelection<?> aliasedSelection) {
            aliasedSelection.getSelection().visitSelection(this);
        }

        @Override
        public void visit(CompoundSelection<?> compoundSelection) {
            for (Selection<?> selection : compoundSelection.getCompoundSelectionItems()) {
                if (selection instanceof ISelection<?> iSelection) {
                    iSelection.visitSelection(this);
                }
            }
        }

        @Override
        public void visit(ConjunctionPredicate conjunction) {
            collectAll(conjunction.getPredicates());
        }

        @Override
        public void visit(DisjunctionPredicate disjunction) {
            collectAll(disjunction.getPredicates());
        }

        @Override
        public void visit(NegatedPredicate negate) {
            collect(negate.getNegated());
        }

        @Override
        public void visit(InPredicate<?> inPredicate) {
            collect(inPredicate.getExpression());
            collectAll(inPredicate.getValues());
        }

        @Override
        public void visit(UnaryPredicate unaryPredicate) {
            collect(unaryPredicate.getExpression());
        }

        @Override
        public void visit(BetweenPredicate betweenPredicate) {
            collect(betweenPredicate.getValue());
            collect(betweenPredicate.getFrom());
            collect(betweenPredicate.getTo());
        }

        @Override
        public void visit(BinaryPredicate binaryPredicate) {
            collect(binaryPredicate.getLeftExpression());
            collect(binaryPredicate.getRightExpression());
        }

        @Override
        public void visit(LikePredicate likePredicate) {
            collect(likePredicate.getExpression());
            collect(likePredicate.getPattern());
            if (likePredicate.getEscapeChar() != null) {
                collect(likePredicate.getEscapeChar());
            }
        }

        @Override
        public void visit(ExistsSubqueryPredicate existsSubqueryPredicate) {
            parameters.addAll(existsSubqueryPredicate.getSubquery().getParameters());
        }
    }

}
