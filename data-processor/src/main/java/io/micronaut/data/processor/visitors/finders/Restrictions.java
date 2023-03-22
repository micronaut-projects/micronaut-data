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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Restrictions.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class Restrictions {

    private static final List<PropertyRestriction> PROPERTY_RESTRICTIONS_LIST = Arrays.stream(Restrictions.class.getClasses())
            .filter(clazz -> PropertyRestriction.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers()))
            .map(clazz -> {
                try {
                    return (PropertyRestriction) clazz.getDeclaredConstructor().newInstance();
                } catch (Throwable e) {
                    return null;
                }
            }).collect(Collectors.toList());

    private static final List<Restriction> RESTRICTIONS_LIST = Arrays.stream(Restrictions.class.getClasses())
            .filter(clazz -> Restriction.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers()))
            .map(clazz -> {
                try {
                    return (Restriction) clazz.getDeclaredConstructor().newInstance();
                } catch (Throwable e) {
                    return null;
                }
            }).collect(Collectors.toList());

    public static final Map<String, PropertyRestriction> PROPERTY_RESTRICTIONS_MAP = PROPERTY_RESTRICTIONS_LIST.stream()
            .collect(Collectors.toMap(PropertyRestriction::getName, p -> p, (a, b) -> a, TreeMap::new));

    public static final Map<String, Restriction> RESTRICTIONS_MAP = RESTRICTIONS_LIST.stream()
            .collect(Collectors.toMap(Restriction::getName, p -> p, (a, b) -> a, TreeMap::new));

    @Nullable
    public static <T> PropertyRestriction<T> findPropertyRestriction(String name) {
        return PROPERTY_RESTRICTIONS_MAP.get(name);
    }

    @Nullable
    public static <T> Restriction<T> findRestriction(String name) {
        return RESTRICTIONS_MAP.get(name);
    }

    /**
     * Ids restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyIds<T> implements Restriction<T> {

        @Override
        public String getName() {
            return "Ids";
        }

        @Override
        public int getRequiredParameters() {
            return 1;
        }

        @Override
        public Predicate find(PersistentEntityRoot<?> entityRoot,
                              PersistentEntityCriteriaBuilder cb,
                              ParameterExpression<T>[] parameters) {
            return entityRoot.id().in(parameters[0]);
        }
    }

    /**
     * Greater than expression.
     *
     * @param <T> The property type
     */
    public static class PropertyGreaterThan<T extends Comparable<? super T>> extends SinglePropertyExpressionRestriction<T> {

        public PropertyGreaterThan() {
            super(PersistentEntityCriteriaBuilder::greaterThan);
        }

        @Override
        public String getName() {
            return "GreaterThan";
        }
    }


    /**
     * Same as {@link PropertyGreaterThan}.
     *
     * @param <T> The property type
     */
    public static class After<T extends Comparable<? super T>> extends PropertyGreaterThan<T> {
        @Override
        public String getName() {
            return "After";
        }
    }

    /**
     * Same as {@link PropertyLessThan}.
     *
     * @param <T> The property type
     */
    public static class Before<T extends Comparable<? super T>> extends PropertyLessThan<T> {
        @Override
        public String getName() {
            return "Before";
        }
    }

    /**
     * Greater than equals.
     *
     * @param <T> The property type
     */
    public static class PropertyGreaterThanEquals<T extends Comparable<? super T>> extends SinglePropertyExpressionRestriction<T> {

        public PropertyGreaterThanEquals() {
            super(PersistentEntityCriteriaBuilder::greaterThanOrEqualTo);
        }

        @Override
        public String getName() {
            return "GreaterThanEquals";
        }
    }

    /**
     * Less than.
     *
     * @param <T> The property type
     */
    public static class PropertyLessThan<T extends Comparable<? super T>> extends SinglePropertyExpressionRestriction<T> {

        public PropertyLessThan() {
            super(PersistentEntityCriteriaBuilder::lessThan);
        }

        @Override
        public String getName() {
            return "LessThan";
        }
    }

    /**
     * Less than equals.
     *
     * @param <T> The property type
     */
    public static class PropertyLessThanEquals<T extends Comparable<? super T>> extends SinglePropertyExpressionRestriction<T> {

        public PropertyLessThanEquals() {
            super(PersistentEntityCriteriaBuilder::lessThanOrEqualTo);
        }

        @Override
        public String getName() {
            return "LessThanEquals";
        }
    }

    /**
     * Like criterion.
     */
    public static class PropertyLike extends SinglePropertyExpressionRestriction<String> {

        public PropertyLike() {
            super(PersistentEntityCriteriaBuilder::like);
        }

        @Override
        public String getName() {
            return "Like";
        }
    }

    /**
     * Regex criterion.
     */
    public static class PropertyRegex extends SinglePropertyExpressionRestriction<String> {

        public PropertyRegex() {
            super(PersistentEntityCriteriaBuilder::regex);
        }

        @Override
        public String getName() {
            return "Regex";
        }
    }

    /**
     * Contains with criterion.
     */
    public static class PropertyContains extends SinglePropertyExpressionRestriction<String> {

        public PropertyContains() {
            super(PersistentEntityCriteriaBuilder::containsString);
        }

        @Override
        public String getName() {
            return "Contains";
        }
    }

    /**
     * Contains with criterion IgnoreCase.
     */
    public static class PropertyContainsIgnoreCase extends SinglePropertyExpressionRestriction<String> {

        public PropertyContainsIgnoreCase() {
            super(PersistentEntityCriteriaBuilder::containsStringIgnoreCase);
        }

        @Override
        public String getName() {
            return "ContainsIgnoreCase";
        }
    }

    /**
     * Contains with criterion.
     */
    public static class PropertyContainingIgnoreCase extends PropertyContainsIgnoreCase {

        @Override
        public String getName() {
            return "ContainingIgnoreCase";
        }

    }

    /**
     * Contains with criterion.
     */
    public static class PropertyContaining extends PropertyContains {

        @Override
        public String getName() {
            return "Containing";
        }

    }

    /**
     * Starts with criterion.
     */
    public static class PropertyStartingWith extends PropertyStartsWith {

        @Override
        public String getName() {
            return "StartingWith";
        }
    }

    /**
     * Starts with criterion.
     */
    public static class PropertyStartsWith extends SinglePropertyExpressionRestriction<String> {

        public PropertyStartsWith() {
            super(PersistentEntityCriteriaBuilder::startsWithString);
        }

        @Override
        public String getName() {
            return "StartsWith";
        }
    }

    /**
     * Starts with criterion.
     */
    public static class PropertyStartingWithIgnoreCase extends PropertyStartsWithIgnoreCase {

        @Override
        public String getName() {
            return "StartingWithIgnoreCase";
        }
    }

    /**
     * Starts with criterion.
     */
    public static class PropertyStartsWithIgnoreCase extends SinglePropertyExpressionRestriction<String> {

        public PropertyStartsWithIgnoreCase() {
            super(PersistentEntityCriteriaBuilder::startsWithStringIgnoreCase);
        }

        @Override
        public String getName() {
            return "StartsWithIgnoreCase";
        }
    }

    /**
     * Ends with criterion.
     */
    public static class PropertyEndsWith extends PropertyEndingWith {

        @Override
        public String getName() {
            return "EndsWith";
        }
    }

    /**
     * Ends with criterion.
     */
    public static class PropertyEndingWith extends SinglePropertyExpressionRestriction<String> {

        public PropertyEndingWith() {
            super(PersistentEntityCriteriaBuilder::endingWithString);
        }

        @Override
        public String getName() {
            return "EndingWith";
        }
    }

    /**
     * Ends with criterion.
     */
    public static class PropertyEndingWithIgnoreCase extends SinglePropertyExpressionRestriction<String> {

        public PropertyEndingWithIgnoreCase() {
            super(PersistentEntityCriteriaBuilder::endingWithStringIgnoreCase);
        }

        @Override
        public String getName() {
            return "EndingWithIgnoreCase";
        }
    }


    /**
     * Ends with criterion.
     */
    public static class PropertyEndsWithIgnoreCase extends PropertyEndingWithIgnoreCase {

        @Override
        public String getName() {
            return "EndsWithIgnoreCase";
        }
    }

    /**
     * Case insensitive like.
     */
    public static class PropertyIlike extends SinglePropertyExpressionRestriction<String> {

        public PropertyIlike() {
            super(PersistentEntityCriteriaBuilder::ilikeString);
        }

        @Override
        public String getName() {
            return "Ilike";
        }
    }

    /**
     * Regex like.
     */
    public static class PropertyRlike extends SinglePropertyExpressionRestriction<String> {

        public PropertyRlike() {
            super(PersistentEntityCriteriaBuilder::rlikeString);
        }

        @Override
        public String getName() {
            return "Rlike";
        }
    }

    /**
     * NotInList restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyNotInList<T> extends PropertyNotIn<T> {

        @Override
        public String getName() {
            return "NotInList";
        }

    }

    /**
     * NotIn restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyNotIn<T> implements PropertyRestriction<T> {

        @Override
        public String getName() {
            return "NotIn";
        }

        @Override
        public int getRequiredParameters() {
            return 1;
        }

        @Override
        public Predicate find(PersistentEntityRoot<?> entityRoot,
                              PersistentEntityCriteriaBuilder cb,
                              Expression<T> expression,
                              ParameterExpression<T>[] parameters) {
            return expression.in(parameters[0]).not();
        }
    }

    /**
     * InList restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyInList<T> extends PropertyIn<T> {

        @Override
        public String getName() {
            return "InList";
        }

    }

    /**
     * In restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyIn<T> implements PropertyRestriction<T> {

        @Override
        public String getName() {
            return "In";
        }

        @Override
        public int getRequiredParameters() {
            return 1;
        }

        @Override
        public Predicate find(PersistentEntityRoot<?> entityRoot,
                              PersistentEntityCriteriaBuilder cb,
                              Expression<T> expression,
                              ParameterExpression<T>[] parameters) {
            return expression.in(parameters[0]);
        }
    }

    /**
     * InRange restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyInRange<T extends Comparable<? super T>> extends PropertyBetween<T> {

        @Override
        public String getName() {
            return "InRange";
        }
    }

    /**
     * IsTrue restriction.
     */
    public static class PropertyIsTrue extends SinglePropertyRestriction<Boolean> {

        public PropertyIsTrue() {
            super(PersistentEntityCriteriaBuilder::isTrue);
        }

        @Override
        public String getName() {
            return "True";
        }
    }

    /**
     * IsFalse restriction.
     */
    public static class PropertyIsFalse extends SinglePropertyRestriction<Boolean> {

        public PropertyIsFalse() {
            super(PersistentEntityCriteriaBuilder::isFalse);
        }

        @Override
        public String getName() {
            return "False";
        }
    }

    /**
     * IsNotNull restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyIsNotNull<T> extends SinglePropertyRestriction<T> {

        public PropertyIsNotNull() {
            super(PersistentEntityCriteriaBuilder::isNotNull);
        }

        @Override
        public String getName() {
            return "IsNotNull";
        }
    }

    /**
     * IsNull restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyIsNull<T> extends SinglePropertyRestriction<T> {

        public PropertyIsNull() {
            super(PersistentEntityCriteriaBuilder::isNull);
        }

        @Override
        public String getName() {
            return "IsNull";
        }
    }

    /**
     * IsEmpty restriction.
     */
    public static class PropertyIsEmpty extends SinglePropertyRestriction<String> {

        public PropertyIsEmpty() {
            super(PersistentEntityCriteriaBuilder::isEmptyString);
        }

        @Override
        public String getName() {
            return "IsEmpty";
        }
    }

    /**
     * IsNotEmpty restriction.
     */
    public static class PropertyIsNotEmpty extends SinglePropertyRestriction<String> {

        public PropertyIsNotEmpty() {
            super(PersistentEntityCriteriaBuilder::isNotEmptyString);
        }

        @Override
        public String getName() {
            return "IsNotEmpty";
        }
    }

    /**
     * Between restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyBetween<T extends Comparable<? super T>> implements PropertyRestriction<T> {

        @Override
        public String getName() {
            return "Between";
        }

        @Override
        public int getRequiredParameters() {
            return 2;
        }

        @Override
        public Predicate find(PersistentEntityRoot<?> entityRoot,
                              PersistentEntityCriteriaBuilder cb,
                              Expression<T> expression,
                              ParameterExpression<T>[] parameters) {
            return cb.between(expression, parameters[0], parameters[1]);
        }

    }

    /**
     * Equals restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyEquals<T> extends PropertyEqual<T> {

        @Override
        public String getName() {
            return "Equals";
        }
    }

    /**
     * Equal restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyEqual<T> extends SinglePropertyExpressionRestriction<T> {

        public PropertyEqual() {
            super(CriteriaBuilder::equal);
        }

        @Override
        public String getName() {
            return "Equal";
        }
    }

    /**
     * EqualsIgnoreCase restriction.
     */
    public static class PropertyStringEqualsIgnoreCase extends PropertyStringEqualIgnoreCase {

        @Override
        public String getName() {
            return "EqualsIgnoreCase";
        }
    }

    /**
     * EqualIgnoreCase restriction.
     */
    public static class PropertyStringEqualIgnoreCase extends SinglePropertyExpressionRestriction<String> {

        public PropertyStringEqualIgnoreCase() {
            super(PersistentEntityCriteriaBuilder::equalStringIgnoreCase);
        }

        @Override
        public String getName() {
            return "EqualIgnoreCase";
        }
    }

    /**
     * PropertyNotEquals restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyNotEquals<T> extends PropertyNotEqual<T> {

        @Override
        public String getName() {
            return "NotEquals";
        }

    }

    /**
     * PropertyNotEqual restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyNotEqual<T> extends SinglePropertyExpressionRestriction<T> {

        public PropertyNotEqual() {
            super(CriteriaBuilder::notEqual);
        }

        @Override
        public String getName() {
            return "NotEqual";
        }

    }

    private abstract static class SinglePropertyRestriction<T> implements PropertyRestriction<T> {

        private final OneExpressionOp<T> func;

        public SinglePropertyRestriction(OneExpressionOp<T> func) {
            this.func = func;
        }

        @Override
        public int getRequiredParameters() {
            return 0;
        }

        @Override
        public Predicate find(PersistentEntityRoot<?> entityRoot,
                              PersistentEntityCriteriaBuilder cb,
                              Expression<T> expression,
                              ParameterExpression<T>[] parameters) {
            return func.apply(cb, expression);
        }
    }

    private abstract static class SinglePropertyExpressionRestriction<T> implements PropertyRestriction<T> {

        private final TwoExpressionOp<T> func;

        public SinglePropertyExpressionRestriction(TwoExpressionOp<T> func) {
            this.func = func;
        }

        @Override
        public int getRequiredParameters() {
            return 1;
        }

        @Override
        public Predicate find(PersistentEntityRoot<?> entityRoot,
                              PersistentEntityCriteriaBuilder cb,
                              Expression<T> expression,
                              ParameterExpression<T>[] parameters) {
            return func.apply(cb, expression, parameters[0]);
        }
    }


    /**
     * Property restriction.
     *
     * @param <T> The expression type
     */
    public interface PropertyRestriction<T> {

        String getName();

        int getRequiredParameters();

        @NonNull
        Predicate find(@NonNull PersistentEntityRoot<?> entityRoot,
                       @NonNull PersistentEntityCriteriaBuilder cb,
                       @NonNull Expression<T> expression,
                       @NonNull ParameterExpression<T>[] parameters);
    }

    /**
     * Restriction.
     *
     * @param <T> The expression type
     */
    public interface Restriction<T> {

        String getName();

        int getRequiredParameters();

        @NonNull
        Predicate find(@NonNull PersistentEntityRoot<?> entityRoot,
                       @NonNull PersistentEntityCriteriaBuilder cb,
                       @NonNull ParameterExpression<T>[] parameters);

    }

    private interface TwoExpressionOp<T> {

        Predicate apply(PersistentEntityCriteriaBuilder cb, Expression<T> expression, Expression<T> parameter);

    }

    private interface OneExpressionOp<T> {

        Predicate apply(PersistentEntityCriteriaBuilder cb, Expression<T> expression);

    }

    /**
     * Array contains restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyArrayContains<T> extends SinglePropertyExpressionRestriction<T> {

        public PropertyArrayContains() {
            super(PersistentEntityCriteriaBuilder::arrayContains);
        }

        @Override
        public String getName() {
            return "ArrayContains";
        }
    }

    /**
     * Collection contains restriction.
     *
     * @param <T> The property type
     */
    public static class PropertyCollectionContains<T> extends PropertyArrayContains<T> {

        @Override
        public String getName() {
            return "CollectionContains";
        }
    }
}
