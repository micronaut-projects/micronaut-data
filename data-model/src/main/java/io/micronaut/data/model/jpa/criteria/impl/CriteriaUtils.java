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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Subquery;

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

    public static boolean isNumeric(@NonNull Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return Number.class.isAssignableFrom(ReflectionUtils.getPrimitiveType(clazz));
        }
        return Number.class.isAssignableFrom(clazz);
    }

    public static boolean isBoolean(@NonNull Class<?> clazz) {
        return Boolean.class.isAssignableFrom(clazz) || boolean.class.isAssignableFrom(clazz);
    }

    public static boolean isComparable(@NonNull Class<?> clazz) {
        return Comparable.class.isAssignableFrom(clazz) || isNumeric(clazz) ;
    }

    public static boolean isTextual(@NonNull Class<?> clazz) {
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
                pp.getProperty() == pp.getProperty().getOwner().getVersion()) {
                return true;
            }
            if (binaryPredicate.getRightExpression() instanceof PersistentPropertyPath<?> pp &&
                pp.getProperty() == pp.getProperty().getOwner().getVersion()) {
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

    public static Set<ParameterExpression<?>> extractPredicateParameters(Expression<?> predicate) {
        if (predicate == null) {
            return Collections.emptySet();
        }
        Set<ParameterExpression<?>> properties = new LinkedHashSet<>();
        extractPredicateParameters(predicate, properties);
        return properties;
    }

    private static void extractPredicateParameters(Expression<?> predicate, Set<ParameterExpression<?>> parameters) {
        if (predicate instanceof LiteralExpression<?>) {
            return;
        } else if (predicate instanceof BinaryPredicate binaryPredicate) {
            if (binaryPredicate.getLeftExpression() instanceof ParameterExpression<?> parameterExpression) {
                parameters.add(parameterExpression);
            }
            if (binaryPredicate.getRightExpression() instanceof ParameterExpression<?> parameterExpression) {
                parameters.add(parameterExpression);
            }
        } else if (predicate instanceof InPredicate<?> pp) {
            for (Expression<?> expression : pp.getValues()) {
                if (expression instanceof ParameterExpression<?> parameterExpression) {
                    parameters.add(parameterExpression);
                }
            }
        } else if (predicate instanceof ConjunctionPredicate conjunctionPredicate) {
            for (IExpression<Boolean> pred : conjunctionPredicate.getPredicates()) {
                extractPredicateParameters(pred, parameters);
            }
        } else if (predicate instanceof DisjunctionPredicate disjunctionPredicate) {
            for (IExpression<Boolean> pred : disjunctionPredicate.getPredicates()) {
                extractPredicateParameters(pred, parameters);
            }
        } else {
            throw new IllegalStateException("Unsupported predicate type: " + predicate.getClass().getSimpleName());
        }
    }

}
