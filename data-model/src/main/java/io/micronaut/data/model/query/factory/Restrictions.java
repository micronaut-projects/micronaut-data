/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.query.factory;

import io.micronaut.data.model.query.QueryModel;

/**
 * Factory for creating criterion instances.
 *
 * @author graemerocher
 * @since 1.0
 */
public class Restrictions {

    /**
     * Restricts the property to be equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return An instance of Query.Equals
     */
    public static QueryModel.Equals eq(String property, Object parameter) {
        return new QueryModel.Equals(property, parameter);
    }

    /**
     * Restricts the property to be equal to the given value.
     * @param parameter The parameter that provides the value
     * @return An instance of Query.Equals
     */
    public static QueryModel.IdEquals idEq(Object parameter) {
        return new QueryModel.IdEquals(parameter);
    }

    /**
     * Restricts the property to be equal to the given value.
     * @param parameter The parameter that provides the value
     * @return An instance of Query.Equals
     */
    public static QueryModel.VersionEquals versionEq(Object parameter) {
        return new QueryModel.VersionEquals(parameter);
    }

    /**
     * Restricts the property to be not equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return An instance of Query.Equals
     */

    public static QueryModel.NotEquals ne(String property, Object parameter) {
        return new QueryModel.NotEquals(property, parameter);
    }

    /**
     * Restricts the property to be in the list of given values.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return An instance of Query.In
     */
    public static QueryModel.In in(String property, Object parameter) {
        return new QueryModel.In(property, parameter);
    }

    /**
     * Restricts the property to be in the list of given values.
     * @param property The property
     * @param subquery The subquery
     * @return An instance of Query.In
     */
    public static QueryModel.In in(String property, QueryModel subquery) {
        return new QueryModel.In(property, subquery);
    }

    /**
     * Restricts the property to be in the list of given values.
     * @param property The property
     * @param subquery The subquery
     * @return An instance of Query.In
     */
    public static QueryModel.NotIn notIn(String property, QueryModel subquery) {
        return new QueryModel.NotIn(property, subquery);
    }

    /**
     * Restricts the property match the given String expressions. Expressions use SQL-like % to denote wildcards
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.Like
     */
    public static QueryModel.Like like(String property, Object expression) {
        return new QueryModel.Like(property, expression);
    }

    /**
     * Restricts the property match the given regex expressions.
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.Like
     */
    public static QueryModel.Regex regex(String property, Object expression) {
        return new QueryModel.Regex(property, expression);
    }

    /**
     * Restricts the property match to strings starting with the given value.
     *
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.StartsWith
     */
    public static QueryModel.StartsWith startsWith(String property, Object expression) {
        return new QueryModel.StartsWith(property, expression);
    }

    /**
     * Restricts the property match to strings containing the given value.
     *
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.Constains
     */
    public static QueryModel.Contains contains(String property, Object expression) {
        return new QueryModel.Contains(property, expression);
    }

    /**
     * Restricts the property match to strings ending with the given value.
     *
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.EndsWith
     */
    public static QueryModel.EndsWith endsWith(String property, Object expression) {
        return new QueryModel.EndsWith(property, expression);
    }

    /**
     * Case insensitive like.
     *
     * @param property The property
     * @param expression The expression
     * @return An ILike expression
     */
    public static QueryModel.ILike ilike(String property, Object expression) {
        return new QueryModel.ILike(property, expression);
    }

    /**
     * Restricts the property match the given regular expressions.
     *
     * @param property The property name
     * @param expression The expression
     * @return An instance of Query.RLike
     */
    public static QueryModel.RLike rlike(String property, Object expression) {
        return new QueryModel.RLike(property, expression);
    }

    /**
     * Logical OR.
     * @param a The left criterion
     * @param b The right criterion
     * @return The criterion
     */
    public static QueryModel.Criterion and(QueryModel.Criterion a, QueryModel.Criterion b) {
        return new QueryModel.Conjunction().add(a).add(b);
    }

    /**
     * Logical OR.
     * @param a The left criterion
     * @param b The right criterion
     * @return The criterion
     */
    public static QueryModel.Criterion or(QueryModel.Criterion a, QueryModel.Criterion b) {
        return new QueryModel.Disjunction().add(a).add(b);
    }

    /**
     * Restricts the results by the given property value range.
     *
     * @param property The name of the property
     * @param start The start of the range
     * @param end The end of the range
     * @return The Between instance
     */
    public static QueryModel.Between between(String property, Object start, Object end) {
        return new QueryModel.Between(property, start, end);
    }

    /**
     * Used to restrict a value to be greater than the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The GreaterThan instance
     */
    public static QueryModel.GreaterThan gt(String property, Object parameter) {
        return new QueryModel.GreaterThan(property, parameter);
    }

    /**
     * Used to restrict a value to be less than the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The LessThan instance
     */
    public static QueryModel.LessThan lt(String property, Object parameter) {
        return new QueryModel.LessThan(property, parameter);
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The LessThan instance
     */
    public static QueryModel.GreaterThanEquals gte(String property, Object parameter) {
        return new QueryModel.GreaterThanEquals(property, parameter);
    }

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The LessThan instance
     */
    public static QueryModel.LessThanEquals lte(String property, Object parameter) {
        return new QueryModel.LessThanEquals(property, parameter);
    }

    /**
     * Used to restrict a value to be null.
     *
     * @param property The property name
     * @return The IsNull instance
     */
    public static QueryModel.IsNull isNull(String property) {
        return new QueryModel.IsNull(property);
    }

    /**
     * Used to restrict a value to be empty (such as a blank string or an empty collection).
     *
     * @param property The property name
     * @return The IsEmpty instance
     */
    public static QueryModel.IsEmpty isEmpty(String property) {
        return new QueryModel.IsEmpty(property);
    }

    /**
     * Used to restrict a value to be not empty (such as a non-blank string).
     *
     * @param property The property name
     * @return The IsEmpty instance
     */
    public static QueryModel.IsNotEmpty isNotEmpty(String property) {
        return new QueryModel.IsNotEmpty(property);
    }

    /**
     * Used to restrict a value to be null.
     *
     * @param property The property name
     * @return The IsNull instance
     */
    public static QueryModel.IsNotNull isNotNull(String property) {
        return new QueryModel.IsNotNull(property);
    }

    /**
     * Used to restrict a value to be true.
     *
     * @param property The property name
     * @return The true instance
     */
    public static QueryModel.IsTrue isTrue(String property) {
        return new QueryModel.IsTrue(property);
    }

    /**
     * Used to restrict a value to be false.
     *
     * @param property The property name
     * @return The true instance
     */
    public static QueryModel.IsFalse isFalse(String property) {
        return new QueryModel.IsFalse(property);
    }

    /**
     * Used to restrict the size of a collection property.
     *
     * @param property The property
     * @param size The size to restrict
     * @return The result
     */
    public static QueryModel.SizeEquals sizeEq(String property, Object size) {
        return new QueryModel.SizeEquals(property, size);
    }

    /**
     * Used to restrict the size of a collection property to be greater than the given value.
     *
     * @param property The property
     * @param size The size to restrict
     * @return The result
     */
    public static QueryModel.SizeGreaterThan sizeGt(String property, Object size) {
        return new QueryModel.SizeGreaterThan(property, size);
    }

    /**
     * Used to restrict the size of a collection property to be greater than or equal to the given value.
     *
     * @param property The property
     * @param size The size to restrict
     * @return The result
     */
    public static QueryModel.SizeGreaterThanEquals sizeGe(String property, Object size) {
        return new QueryModel.SizeGreaterThanEquals(property, size);
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than or equal to the given size.
     *
     * @param property The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public static QueryModel.SizeLessThanEquals sizeLe(String property, Object size) {
        return new QueryModel.SizeLessThanEquals(property, size);
    }

    /**
     * Creates a Criterion that contrains a collection property to be less than to the given size.
     *
     * @param property The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public static QueryModel.SizeLessThan sizeLt(String property, Object size) {
        return new QueryModel.SizeLessThan(property, size);
    }

    /**
     * Creates a Criterion that contrains a collection property to be not equal to the given size.
     *
     * @param property The property name
     * @param size The size to constrain by
     *
     * @return A Criterion instance
     */
    public static QueryModel.SizeNotEquals sizeNe(String property, Object size) {
        return new QueryModel.SizeNotEquals(property, size);
    }

    /**
     * Constraints a property to be equal to a specified other property.
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion instance
     */
    public static QueryModel.EqualsProperty eqProperty(String propertyName, String otherPropertyName) {
        return new QueryModel.EqualsProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be not equal to a specified other property.
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return This criterion instance
     */
    public static QueryModel.NotEqualsProperty neProperty(String propertyName, String otherPropertyName) {
        return new QueryModel.NotEqualsProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be greater than a specified other property.
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static QueryModel.GreaterThanProperty gtProperty(String propertyName, String otherPropertyName) {
        return new QueryModel.GreaterThanProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be greater than or equal to a specified other property.
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static QueryModel.GreaterThanEqualsProperty geProperty(String propertyName, String otherPropertyName) {
        return new QueryModel.GreaterThanEqualsProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be less than a specified other property.
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static QueryModel.LessThanProperty ltProperty(String propertyName, String otherPropertyName) {
        return new QueryModel.LessThanProperty(propertyName, otherPropertyName);
    }

    /**
     * Constraints a property to be less than or equal to a specified other property.
     *
     * @param propertyName      The property
     * @param otherPropertyName The other property
     * @return The criterion
     */
    public static QueryModel.LessThanEqualsProperty leProperty(String propertyName, String otherPropertyName) {
        return new QueryModel.LessThanEqualsProperty(propertyName, otherPropertyName);
    }

}
