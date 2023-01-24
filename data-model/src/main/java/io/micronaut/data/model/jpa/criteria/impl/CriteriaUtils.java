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
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyBinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PersistentPropertyInValuesPredicate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static List<IExpression<Boolean>> requireBoolExpressions(Iterable<? extends Expression<?>> restrictions) {
        return CollectionUtils.iterableToList(restrictions).stream().map(CriteriaUtils::requireBoolExpression).collect(Collectors.toList());
    }

    public static IExpression<Boolean> requireBoolExpression(Expression<?> exp) {
        if (exp instanceof IExpression) {
            IExpression<Boolean> expression = (IExpression<Boolean>) exp;
            if (!expression.isBoolean()) {
                throw new IllegalStateException("Expected a boolean expression! Got: " + exp);
            }
            return expression;
        }
        throw new IllegalStateException("Expression is unknown! Got: " + exp);
    }

    public static <T> PersistentPropertyPath<T> requireBoolProperty(Expression<Boolean> exp) {
        if (exp instanceof PersistentPropertyPath) {
            PersistentPropertyPath<T> propertyPath = (PersistentPropertyPath<T>) exp;
            if (!propertyPath.isBoolean()) {
                throw new IllegalStateException("Expected a boolean expression property! Got: " + exp);
            }
            return propertyPath;
        }
        throw new IllegalStateException("Expression is expected to be a property path! Got: " + exp);
    }

    public static <T> PersistentPropertyPath<T> requireNumericProperty(Expression<T> exp) {
        if (exp instanceof PersistentPropertyPath<T> propertyPath) {
            if (!propertyPath.isNumeric()) {
                throw new IllegalStateException("Expected a numeric expression property! Got: " + exp);
            }
            return propertyPath;
        }
        throw new IllegalStateException("Expression is expected to be a property path! Got: " + exp);
    }

    public static <T> PersistentPropertyPath<T> requireComparableProperty(Expression<T> exp) {
        if (exp instanceof PersistentPropertyPath<T> propertyPath) {
            if (!propertyPath.isComparable()) {
                throw new IllegalStateException("Expected a comparable expression property! Got: " + exp);
            }
            return propertyPath;
        }
        throw new IllegalStateException("Expression is expected to be a property path! Got: " + exp);
    }

    public static <T> Expression<T> requireComparablePropertyParameterOrLiteral(Expression<T> exp) {
        exp = requirePropertyParameterOrLiteral(exp);
        if (exp instanceof PersistentPropertyPath<?> propertyPath) {
            if (!propertyPath.isComparable()) {
                throw new IllegalStateException("Expected a comparable expression property! Got: " + exp);
            }
            return exp;
        }
        if (exp instanceof ParameterExpression) {
            // TODO: validation
            return exp;
        }
        if (exp instanceof LiteralExpression) {
            if (((LiteralExpression<T>) exp).getValue() instanceof Comparable) {
                return exp;
            }
            throw new IllegalStateException("Expected a comparable expression property! Got: " + exp);
        }
        return exp;
    }

    public static <T> Expression<T> requireNumericPropertyParameterOrLiteral(Expression<T> exp) {
        exp = requirePropertyParameterOrLiteral(exp);
        if (exp instanceof PersistentPropertyPath<?> propertyPath) {
            if (!propertyPath.isNumeric()) {
                throw new IllegalStateException("Expected a numeric expression property! Got: " + exp);
            }
            return exp;
        }
        if (exp instanceof ParameterExpression) {
            // TODO: validation
            return exp;
        }
        if (exp instanceof LiteralExpression) {
            // TODO: validation
            return exp;
        }
        return exp;
    }

    public static <T> ParameterExpression<T> requireParameter(Expression<T> exp) {
        if (exp instanceof ParameterExpression) {
            return (ParameterExpression<T>) exp;
        }
        throw new IllegalStateException("Expression is expected to be a parameter! Got: " + exp);
    }

    public static <T> PersistentPropertyPath<T> requireProperty(Expression<? extends T> exp) {
        if (exp instanceof PersistentPropertyPath) {
            return (PersistentPropertyPath<T>) exp;
        }
        throw new IllegalStateException("Expression is expected to be a property path! Got: " + exp);
    }

    public static <T> Expression<T> requirePropertyParameterOrLiteral(Expression<T> exp) {
        if (exp instanceof PersistentPropertyPath || exp instanceof ParameterExpression || exp instanceof LiteralExpression) {
            return exp;
        }
        throw new IllegalStateException("Expression is expected to be a property path, a parameter or literal! Got: " + exp);
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
        if (predicate instanceof PersistentPropertyBinaryPredicate<?> pp) {
            return pp.getProperty() == pp.getProperty().getOwner().getVersion();
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
        if (predicate instanceof PersistentPropertyBinaryPredicate<?> pp) {
            if (pp.getExpression() instanceof ParameterExpression) {
                parameters.add((ParameterExpression<?>) pp.getExpression());
            }
        } else if (predicate instanceof PersistentPropertyInValuesPredicate<?> pp) {
            for (Expression<?> expression : pp.getValues()) {
                if (expression instanceof ParameterExpression) {
                    parameters.add((ParameterExpression<?>) expression);
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
        }
    }

}
