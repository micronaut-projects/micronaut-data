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
import org.jspecify.annotations.Nullable;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
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
     * The insert criteria.
     * @param targetEntity The target entity
     * @param <T> The entity type
     * @return The insert criteria
     * @since 5.0
     */
    
    <T> PersistentEntityCriteriaInsert<T> createCriteriaInsert(Class<T> targetEntity);

    /**
     * Create an ordering.
     *
     * @param x          expression used to define the ordering
     * @param ascending  If ascending should be use
     * @param ignoreCase If ignore case should be used
     * @return ascending ordering corresponding to the expression
     */
    
    Order sort(Expression<?> x, boolean ascending, boolean ignoreCase);

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
     * Creates an case-insensitive like predicate.
     *
     * @param x The expression
     * @param pattern The pattern
     * @return a new predicate
     */
    Predicate ilike(Expression<String> x, Expression<String> pattern);

    /**
     * Creates an case-insensitive like predicate.
     *
     * @param x The expression
     * @param pattern The pattern
     * @return a new predicate
     */
    default Predicate ilike(Expression<String> x, String pattern) {
        return ilike(x, literal(pattern));
    }

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

    /**
     * Creates a MongoDB {@code $text} predicate.
     *
     * @param search The full-text search expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate text(Expression<String> search);

    /**
     * Creates a MongoDB {@code $text} predicate with optional text-search options.
     *
     * @param search The full-text search expression
     * @param language The optional language override expression
     * @param caseSensitive The optional case-sensitive flag expression
     * @param diacriticSensitive The optional diacritic-sensitive flag expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate text(Expression<String> search,
                   @Nullable Expression<String> language,
                   @Nullable Expression<Boolean> caseSensitive,
                   @Nullable Expression<Boolean> diacriticSensitive);

    /**
     * Creates a MongoDB {@code $text} predicate.
     *
     * @param search The full-text search term
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate text(String search) {
        return text(literal(search));
    }

    /**
     * Creates a MongoDB {@code $text} predicate with optional text-search options.
     *
     * @param search The full-text search term
     * @param language The optional language override
     * @param caseSensitive The optional case-sensitive flag
     * @param diacriticSensitive The optional diacritic-sensitive flag
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate text(String search,
                           @Nullable String language,
                           @Nullable Boolean caseSensitive,
                           @Nullable Boolean diacriticSensitive) {
        return text(
            literal(search),
            language == null ? null : literal(language),
            caseSensitive == null ? null : literal(caseSensitive),
            diacriticSensitive == null ? null : literal(diacriticSensitive)
        );
    }

    /**
     * Creates a MongoDB {@code $geoWithin} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate geoWithin(Expression<?> expression, Expression<?> geometry);

    /**
     * Creates a MongoDB {@code $geoWithin} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry value
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate geoWithin(Expression<?> expression, Object geometry) {
        return geoWithin(expression, literal(geometry));
    }

    /**
     * Creates a MongoDB {@code $geoIntersects} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate geoIntersects(Expression<?> expression, Expression<?> geometry);

    /**
     * Creates a MongoDB {@code $geoIntersects} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry value
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate geoIntersects(Expression<?> expression, Object geometry) {
        return geoIntersects(expression, literal(geometry));
    }

    /**
     * Creates a MongoDB {@code $near} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate near(Expression<?> expression, Expression<?> geometry);

    /**
     * Creates a MongoDB {@code $near} predicate with optional distance bounds.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry expression
     * @param minDistance The optional minimum distance expression
     * @param maxDistance The optional maximum distance expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate near(Expression<?> expression,
                   Expression<?> geometry,
                   @Nullable Expression<? extends Number> minDistance,
                   @Nullable Expression<? extends Number> maxDistance);

    /**
     * Creates a MongoDB {@code $near} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry value
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate near(Expression<?> expression, Object geometry) {
        return near(expression, literal(geometry));
    }

    /**
     * Creates a MongoDB {@code $near} predicate with optional distance bounds.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry value
     * @param minDistance The optional minimum distance
     * @param maxDistance The optional maximum distance
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate near(Expression<?> expression,
                           Object geometry,
                           @Nullable Number minDistance,
                           @Nullable Number maxDistance) {
        return near(expression,
            literal(geometry),
            minDistance == null ? null : literal(minDistance),
            maxDistance == null ? null : literal(maxDistance));
    }

    /**
     * Creates a MongoDB {@code $nearSphere} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate nearSphere(Expression<?> expression, Expression<?> geometry);

    /**
     * Creates a MongoDB {@code $nearSphere} predicate with optional distance bounds.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry expression
     * @param minDistance The optional minimum distance expression
     * @param maxDistance The optional maximum distance expression
     * @return a new predicate
     * @since 5.0.0
     */
    Predicate nearSphere(Expression<?> expression,
                         Expression<?> geometry,
                         @Nullable Expression<? extends Number> minDistance,
                         @Nullable Expression<? extends Number> maxDistance);

    /**
     * Creates a MongoDB {@code $nearSphere} predicate.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry value
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate nearSphere(Expression<?> expression, Object geometry) {
        return nearSphere(expression, literal(geometry));
    }

    /**
     * Creates a MongoDB {@code $nearSphere} predicate with optional distance bounds.
     *
     * @param expression The geospatial property expression
     * @param geometry The geometry value
     * @param minDistance The optional minimum distance
     * @param maxDistance The optional maximum distance
     * @return a new predicate
     * @since 5.0.0
     */
    default Predicate nearSphere(Expression<?> expression,
                                 Object geometry,
                                 @Nullable Number minDistance,
                                 @Nullable Number maxDistance) {
        return nearSphere(expression,
            literal(geometry),
            minDistance == null ? null : literal(minDistance),
            maxDistance == null ? null : literal(maxDistance));
    }
}
