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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * The persistent entity criteria builder.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityCriteriaBuilder extends CriteriaBuilder {

    @Override
    PersistentEntityCriteriaQuery<Object> createQuery();

    @Override
    <T> PersistentEntityCriteriaQuery<T> createQuery(Class<T> resultClass);

    @Override
    PersistentEntityCriteriaQuery<Tuple> createTupleQuery();

    @Override
    <T> PersistentEntityCriteriaUpdate<T> createCriteriaUpdate(Class<T> targetEntity);

    @Override
    <T> PersistentEntityCriteriaDelete<T> createCriteriaDelete(Class<T> targetEntity);

    /**
     * OR restriction predicate.
     *
     * @param restrictions The restriction
     * @return a new predicate
     */
    Predicate or(Iterable<Predicate> restrictions);

    /**
     * AND restriction predicate.
     *
     * @param restrictions The restriction
     * @return a new predicate
     */
    Predicate and(Iterable<Predicate> restrictions);

    /**
     * Checks if the expression is empty.
     *
     * @param expression The expression
     * @return a new predicate
     */
    Predicate isEmptyString(Expression<String> expression);

    /**
     * Checks if the expression is not empty.
     *
     * @param expression The expression
     * @return a new predicate
     */
    Predicate isNotEmptyString(Expression<String> expression);

    /**
     * Creates a rlike predicate between an expression x and y.
     *
     * @param x The expression
     * @param y The expression
     * @return a new predicate
     */
    Predicate rlikeString(Expression<String> x, Expression<String> y);

    /**
     * Creates an ilike predicate between an expression x and y.
     *
     * @param x The expression
     * @param y The expression
     * @return a new predicate
     */
    Predicate ilikeString(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x starts with the expression y.
     *
     * @param x The expression
     * @param y The expression
     * @return a new predicate
     */
    Predicate startsWithString(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x ending with the expression y.
     *
     * @param x The expression
     * @param y The expression
     * @return a new predicate
     */
    Predicate endingWithString(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x contains the expression y.
     *
     * @param x The expression
     * @param y The expression
     * @return a new predicate
     */
    Predicate containsString(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x contains the expression y ignoring case.
     *
     * @param x The expression
     * @param y The expression
     * @return a new predicate
     */
    Predicate containsStringIgnoreCase(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x equals a string y ignoring case.
     *
     * @param x The expression
     * @param y The string
     * @return a new predicate
     */
    Predicate equalStringIgnoreCase(Expression<String> x, String y);

    /**
     * Checks if the expression x equals the expression y ignoring case.
     *
     * @param x The expression
     * @param y The string
     * @return a new predicate
     */
    Predicate equalStringIgnoreCase(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x not equals a string y ignoring case.
     *
     * @param x The expression
     * @param y The string
     * @return a new predicate
     */
    Predicate notEqualStringIgnoreCase(Expression<String> x, String y);

    /**
     * Checks if the expression x not equals the expression y ignoring case.
     *
     * @param x The expression
     * @param y The string
     * @return a new predicate
     */
    Predicate notEqualStringIgnoreCase(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x starts with the expression y ignoring case.
     *
     * @param x The expression
     * @param y The string
     * @return a new predicate
     */
    Predicate startsWithStringIgnoreCase(Expression<String> x, Expression<String> y);

    /**
     * Checks if the expression x ending with the expression y ignoring case.
     *
     * @param x The expression
     * @param y The string
     * @return a new predicate
     */
    Predicate endingWithStringIgnoreCase(Expression<String> x, Expression<String> y);

    /**
     * Create a predicate for testing whether the expression satisfies the given pattern.
     * @param x  string expression
     * @param pattern  string expression
     * @return like predicate
     */
    Predicate regex(Expression<String> x, Expression<String> pattern);

    /**
     * Checks if array contains given expression. Supported by Azure Cosmos Db and MongoDB.
     *
     * @param x The expression (property)
     * @param y The expression (value to be contained in the array represented by x property in the db)
     * @return a new predicate
     * @since 3.9.0
     */
    Predicate arrayContains(Expression<?> x, Expression<?> y);
}
