package io.micronaut.data.model.query;

import io.micronaut.data.model.PersistentEntity;

import javax.annotation.Nonnull;
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
     * Creates a criterion that restricts the id to the given value.
     *
     * @param parameter The parameter that provides the value
     * @return The criteria
     */
    @Nonnull Criteria idEquals(QueryParameter parameter);

    /**
     * Creates a criterion that asserts the given property is empty (such as a blank string).
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @Nonnull Criteria isEmpty(@Nonnull String propertyName);

    /**
     * Creates a criterion that asserts the given property is not empty.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @Nonnull Criteria isNotEmpty(@Nonnull String propertyName);

    /**
     * Creates a criterion that asserts the given property is null.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @Nonnull Criteria isNull(@Nonnull String propertyName);

    /**
     * Creates a criterion that asserts the given property is not null.
     *
     * @param propertyName The property name
     * @return The criteria
     */
    @Nonnull Criteria isNotNull(String propertyName);

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @Nonnull Criteria eq(String propertyName, QueryParameter parameter);

    /**
     * Creates an "equals" Criterion based on the specified property name and value.
     *
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @Nonnull Criteria idEq(QueryParameter parameter);

    /**
     * Creates a "not equals" Criterion based on the specified property name and value
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @Nonnull Criteria ne(@Nonnull String propertyName, @Nonnull QueryParameter parameter);

    /**
     * Restricts the results by the given property value range (inclusive).
     *
     * @param propertyName The property name
     *
     * @param start The start of the range
     * @param finish The end of the range
     * @return The criteria
     */
    @Nonnull Criteria between(@Nonnull String propertyName, @Nonnull QueryParameter start, @Nonnull QueryParameter finish);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @Nonnull Criteria gte(@Nonnull String property, @Nonnull QueryParameter parameter);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @Nonnull Criteria ge(@Nonnull String property, @Nonnull QueryParameter parameter);

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @Nonnull Criteria gt(@Nonnull String property, @Nonnull QueryParameter parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @Nonnull Criteria lte(@Nonnull String property, @Nonnull QueryParameter parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @Nonnull Criteria le(@Nonnull String property, @Nonnull QueryParameter parameter);

    /**
     * Used to restrict a value to be less than or equal to the given value.
     * @param property The property
     * @param parameter The parameter that provides the value
     * @return The Criterion instance
     */
    @Nonnull Criteria lt(@Nonnull String property, @Nonnull QueryParameter parameter);

    /**
     * Creates a like Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @Nonnull Criteria like(@Nonnull String propertyName, @Nonnull QueryParameter parameter);

    /**
     * Creates an ilike Criterion based on the specified property name and value. Unlike a like condition, ilike is case insensitive.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @Nonnull Criteria ilike(@Nonnull String propertyName, @Nonnull QueryParameter parameter);

    /**
     * Creates an rlike Criterion based on the specified property name and value.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @Nonnull Criteria rlike(@Nonnull String propertyName, @Nonnull QueryParameter parameter);

    /**
     * Creates a logical conjunction.
     *
     * @param other The other criteria
     * @return This criteria
     */
    @Nonnull Criteria and(@Nonnull Criteria other);

    /**
     * Creates a logical disjunction.
     *
     * @param other The other criteria
     * @return This criteria
     */
    @Nonnull Criteria or(@Nonnull Criteria other);

    /**
     * Creates a logical negation.
     *
     * @param other The other criteria
     * @return This criteria
     */
    @Nonnull Criteria not(@Nonnull Criteria other);

    /**
     * Creates an "in" Criterion using a subquery.
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    @Nonnull Criteria inList(@Nonnull String propertyName, @Nonnull Query subquery);

    /**
     * Creates an "in" Criterion based on the specified property name and list of values.
     *
     * @param propertyName The property name
     * @param parameter The parameter that provides the value
     *
     * @return The criteria
     */
    @Nonnull Criteria inList(@Nonnull String propertyName, @Nonnull QueryParameter parameter);

    /**
     * Creates a negated "in" Criterion using a subquery.
     *
     * @param propertyName The property name
     * @param subquery The subquery
     *
     * @return The criteria
     */
    @Nonnull Criteria notIn(@Nonnull String propertyName, @Nonnull Query subquery);

    /**
     * Orders by the specified property name (defaults to ascending).
     *
     * @param propertyName The property name to order by
     * @return This criteria
     */
    @Nonnull Criteria order(@Nonnull String propertyName);

//    /**
//     * Adds an order object
//     *
//     * @param o The order object
//     * @return The order object
//     */
//    Criteria order(Query.Order o);

    /**
     * Orders by the specified property name and direction.
     *
     * @param propertyName The property name to order by
     * @param direction Either "asc" for ascending or "desc" for descending
     *
     * @return This criteria
     */
    @Nonnull Criteria order(@Nonnull String propertyName, @Nonnull String direction);

    /**
     * Creates a Criterion that constrains a collection property by size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @Nonnull Criteria sizeEq(@Nonnull String propertyName, @Nonnull QueryParameter size) ;

    /**
     * Creates a Criterion that constrains a collection property to be greater than the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @Nonnull Criteria sizeGt(@Nonnull String propertyName, @Nonnull QueryParameter size);

    /**
     * Creates a Criterion that constrains a collection property to be greater than or equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @Nonnull Criteria sizeGe(@Nonnull String propertyName, @Nonnull QueryParameter size);

    /**
     * Creates a Criterion that constrains a collection property to be less than or equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @Nonnull Criteria sizeLe(@Nonnull String propertyName, @Nonnull QueryParameter size);

    /**
     * Creates a Criterion that constrains a collection property to be less than to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @Nonnull Criteria sizeLt(@Nonnull String propertyName, @Nonnull QueryParameter size);

    /**
     * Creates a Criterion that constrains a collection property to be not equal to the given size.
     *
     * @param propertyName The property name
     * @param size The size to constrain by
     *
     * @return This criteria
     */
    @Nonnull Criteria sizeNe(@Nonnull String propertyName, @Nonnull QueryParameter size);

    /**
     * Constrains a property to be equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @Nonnull Criteria eqProperty(@Nonnull java.lang.String propertyName, @Nonnull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be not equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @Nonnull Criteria neProperty(@Nonnull java.lang.String propertyName, @Nonnull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be greater than a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @Nonnull Criteria gtProperty(@Nonnull java.lang.String propertyName, @Nonnull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be greater than or equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @Nonnull Criteria geProperty(@Nonnull java.lang.String propertyName, @Nonnull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be less than a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @Nonnull Criteria ltProperty(@Nonnull java.lang.String propertyName, @Nonnull java.lang.String otherPropertyName);

    /**
     * Constrains a property to be less than or equal to a specified other property.
     *
     * @param propertyName The property
     * @param otherPropertyName The other property
     * @return This criteria
     */
    @Nonnull Criteria leProperty(java.lang.String propertyName, @Nonnull java.lang.String otherPropertyName);

    /**
     * Apply an "equals" constraint to each property in the key set of a <tt>Map</tt>.
     *
     * @param propertyValues a map from property names to values
     *
     * @return Criterion
     *
     */
    @Nonnull Criteria allEq(@Nonnull Map<String, QueryParameter> propertyValues);

    //===== Subquery methods

    /**
     * Creates a subquery criterion that ensures the given property is equals to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @Nonnull Criteria eqAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @Nonnull Criteria gtAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @Nonnull Criteria ltAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue);
    /**
     * Creates a subquery criterion that ensures the given property is greater than or equals to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @Nonnull Criteria geAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue);
    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to all the given returned values.
     *
     * @param propertyName The property name
     * @param propertyValue A subquery
     * @return This criterion instance
     */
    @Nonnull Criteria leAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @Nonnull Criteria gtSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is greater than or equal to some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @Nonnull Criteria geSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @Nonnull Criteria ltSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue);

    /**
     * Creates a subquery criterion that ensures the given property is less than or equal to some of the given values.
     *
     * @param propertyName The property name
     * @param propertyValue The property value
     *
     * @return This Criteria instance
     */
    @Nonnull Criteria leSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue);

    /**
     * <p>Configures the second-level cache with the default usage of 'read-write' and the default include of 'all' if
     *  the passed argument is true.
     *
     * <code> { cache true } </code>
     *
     * @param shouldCache True if the default cache configuration should be applied
     *
     * @return This Criteria instance
     */
    @Nonnull Criteria cache(boolean shouldCache);

    /**
     * <p>Configures the readOnly property to avoid checking for changes on the objects if the passed argument is true
     *
     * <code> { readOnly true } </code>
     *
     * @param readOnly True to disable dirty checking
     *
     * @return This Criteria instance
     */
    @Nonnull Criteria readOnly(boolean readOnly);
}