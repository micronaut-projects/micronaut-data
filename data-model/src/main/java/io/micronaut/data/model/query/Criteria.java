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
package io.micronaut.data.model.query;

import io.micronaut.core.annotation.NonNull;
import java.util.Map;

/**
 * Interface used for the construction of queries at compilation time an implementation may optionally
 * provide an implementation of this at runtime.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Criteria  {

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria idEq(Object parameter);

    /**
     * Creates that restricts the version to the given value.
     *
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria versionEq(Object parameter);

    /**
     * Creates a criterion that asserts the given property is empty (such as a blank string).
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @NonNull Criteria isEmpty(@NonNull String propertyName);

    /**
     * Creates a criterion that asserts the given property is not empty.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @NonNull Criteria isNotEmpty(@NonNull String propertyName);

    /**
     * Creates a criterion that asserts the given property is null.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @NonNull Criteria isNull(@NonNull String propertyName);

    /**
     * Creates a criterion that asserts the given property is true.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @NonNull Criteria isTrue(@NonNull String propertyName);

    /**
     * Creates a criterion that asserts the given property is false.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @NonNull Criteria isFalse(@NonNull String propertyName);

    /**
     * Creates a criterion that asserts the given property is not null.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @NonNull Criteria isNotNull(String propertyName);

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria eq(String propertyName, Object parameter);

    /**
     * Creates a "not equals" Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria ne(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Restricts the results by the given property value range (inclusive).
     *
     * @param propertyName The property name
     *
     * @param start The start of the range
     * @param finish The end of the range
     * @return The criteria
     */
    @NonNull Criteria between(@NonNull String propertyName, @NonNull Object start, @NonNull Object finish);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @NonNull Criteria gte(@NonNull String property, @NonNull Object parameter);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @NonNull Criteria ge(@NonNull String property, @NonNull Object parameter);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @NonNull Criteria gt(@NonNull String property, @NonNull Object parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @NonNull Criteria lte(@NonNull String property, @NonNull Object parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @NonNull Criteria le(@NonNull String property, @NonNull Object parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @NonNull Criteria lt(@NonNull String property, @NonNull Object parameter);

    /**
     * Creates a like Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria like(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Restricts the property match to strings starting with the given value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria startsWith(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Restricts the property match to strings ending with the given value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria endsWith(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Restricts the property match to strings containing with the given value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria contains(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Creates an ilike Criterion based on the specified property name and value. Unlike a like condition, ilike is case insensitive.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria ilike(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Creates an rlike Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria rlike(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Creates a logical conjunction.
     *
     * @param other The other criteria
     * @return This criteria
     */
    @NonNull Criteria and(@NonNull Criteria other);

    /**
     * Creates a logical disjunction.
     *
     * @param other The other criteria
     * @return This criteria
     */
    @NonNull Criteria or(@NonNull Criteria other);

    /**
     * Creates a logical negation.
     *
     * @param other The other criteria
     * @return This criteria
     */
    @NonNull Criteria not(@NonNull Criteria other);

    /**
     * Creates an "in" Criterion using a subquery.
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    @NonNull Criteria inList(@NonNull String propertyName, @NonNull QueryModel subquery);

    /**
     * Creates an "in" Criterion based on the specified property name and list of values.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @NonNull Criteria inList(@NonNull String propertyName, @NonNull Object parameter);

    /**
     * Creates a negated "in" Criterion using a subquery.
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    @NonNull Criteria notIn(@NonNull String propertyName, @NonNull QueryModel subquery);

    /**
     * Creates a Criterion that constrains a collection property by size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @NonNull Criteria sizeEq(@NonNull String propertyName, @NonNull Object size) ;

    /**
     * Creates a Criterion that constrains a collection property to be greater than the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @NonNull Criteria sizeGt(@NonNull String propertyName, @NonNull Object size);

    /**
     * Creates a Criterion that constrains a collection property to be greater than or equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @NonNull Criteria sizeGe(@NonNull String propertyName, @NonNull Object size);

    /**
     * Creates a Criterion that constrains a collection property to be less than or equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @NonNull Criteria sizeLe(@NonNull String propertyName, @NonNull Object size);

    /**
     * Creates a Criterion that constrains a collection property to be less than to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @NonNull Criteria sizeLt(@NonNull String propertyName, @NonNull Object size);

    /**
     * Creates a Criterion that constrains a collection property to be not equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @NonNull Criteria sizeNe(@NonNull String propertyName, @NonNull Object size);

    /**
     * Constrains a property to be equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @NonNull Criteria eqProperty(@NonNull java.lang.String propertyName, @NonNull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be not equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @NonNull Criteria neProperty(@NonNull java.lang.String propertyName, @NonNull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be greater than a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @NonNull Criteria gtProperty(@NonNull java.lang.String propertyName, @NonNull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be greater than or equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @NonNull Criteria geProperty(@NonNull java.lang.String propertyName, @NonNull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be less than a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @NonNull Criteria ltProperty(@NonNull java.lang.String propertyName, @NonNull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be less than or equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @NonNull Criteria leProperty(java.lang.String propertyName, @NonNull java.lang.String otherPropertyName);

    /**
     * Apply an "equals" constraint to each property in the key set of a {@code Map}.
     *
     * @param propertyValues a map from property names to values
     *
     * @return Criterion
     *
     */
    @NonNull Criteria allEq(@NonNull Map<String, Object> propertyValues);

    //===== Subquery methods

    /**
     * Creates a subquery criterion that ensures the given property is equals to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @NonNull Criteria eqAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @NonNull Criteria gtAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @NonNull Criteria ltAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than or equals to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @NonNull Criteria geAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @NonNull Criteria leAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @NonNull Criteria gtSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than or equal to some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @NonNull Criteria geSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @NonNull Criteria ltSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @NonNull Criteria leSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

}
