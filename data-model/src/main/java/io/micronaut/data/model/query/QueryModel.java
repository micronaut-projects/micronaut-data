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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;

import java.util.*;

/**
 * Main interface for constructing queries at either compilation or runtime.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface QueryModel extends Criteria {

    @NonNull
    @Override
    QueryModel idEquals(QueryParameter parameter);

    @NonNull
    @Override
    QueryModel isEmpty(@NonNull String propertyName);

    @NonNull
    @Override
    QueryModel isNotEmpty(@NonNull String propertyName);

    @NonNull
    @Override
    QueryModel isNull(@NonNull String propertyName);

    @NonNull
    @Override
    QueryModel isTrue(@NonNull String propertyName);

    @NonNull
    @Override
    QueryModel isFalse(@NonNull String propertyName);

    @NonNull
    @Override
    QueryModel isNotNull(String propertyName);

    @NonNull
    @Override
    QueryModel eq(String propertyName, QueryParameter parameter);

    @NonNull
    @Override
    QueryModel idEq(QueryParameter parameter);

    @NonNull
    @Override
    QueryModel ne(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel between(@NonNull String propertyName, @NonNull QueryParameter start, @NonNull QueryParameter finish);

    @NonNull
    @Override
    QueryModel gte(@NonNull String property, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel ge(@NonNull String property, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel gt(@NonNull String property, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel lte(@NonNull String property, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel le(@NonNull String property, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel lt(@NonNull String property, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel like(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel startsWith(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel endsWith(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel contains(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel ilike(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel rlike(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel and(@NonNull Criteria other);

    @NonNull
    @Override
    QueryModel or(@NonNull Criteria other);

    @NonNull
    @Override
    QueryModel not(@NonNull Criteria other);

    @NonNull
    @Override
    QueryModel inList(@NonNull String propertyName, @NonNull QueryModel subquery);

    @NonNull
    @Override
    QueryModel inList(@NonNull String propertyName, @NonNull QueryParameter parameter);

    @NonNull
    @Override
    QueryModel notIn(@NonNull String propertyName, @NonNull QueryModel subquery);

    @NonNull
    @Override
    QueryModel sizeEq(@NonNull String propertyName, @NonNull QueryParameter size);

    @NonNull
    @Override
    QueryModel sizeGt(@NonNull String propertyName, @NonNull QueryParameter size);

    @NonNull
    @Override
    QueryModel sizeGe(@NonNull String propertyName, @NonNull QueryParameter size);

    @NonNull
    @Override
    QueryModel sizeLe(@NonNull String propertyName, @NonNull QueryParameter size);

    @NonNull
    @Override
    QueryModel sizeLt(@NonNull String propertyName, @NonNull QueryParameter size);

    @NonNull
    @Override
    QueryModel sizeNe(@NonNull String propertyName, @NonNull QueryParameter size);

    @NonNull
    @Override
    QueryModel eqProperty(@NonNull String propertyName, @NonNull String otherPropertyName);

    @NonNull
    @Override
    QueryModel neProperty(@NonNull String propertyName, @NonNull String otherPropertyName);

    @NonNull
    @Override
    QueryModel gtProperty(@NonNull String propertyName, @NonNull String otherPropertyName);

    @NonNull
    @Override
    QueryModel geProperty(@NonNull String propertyName, @NonNull String otherPropertyName);

    @NonNull
    @Override
    QueryModel ltProperty(@NonNull String propertyName, @NonNull String otherPropertyName);

    @NonNull
    @Override
    QueryModel leProperty(String propertyName, @NonNull String otherPropertyName);

    @NonNull
    @Override
    QueryModel allEq(@NonNull Map<String, QueryParameter> propertyValues);

    @NonNull
    @Override
    QueryModel eqAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel gtAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel ltAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel geAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel leAll(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel gtSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel geSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel ltSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

    @NonNull
    @Override
    QueryModel leSome(@NonNull String propertyName, @NonNull Criteria propertyValue);

    /**
     * @return The join paths.
     */
    Collection<JoinPath> getJoinPaths();

    /**
     * @return The entity the criteria applies to
     */
    @NonNull
    PersistentEntity getPersistentEntity();

    /**
     * @return The criteria for this query
     */
    @NonNull
    QueryModel.Junction getCriteria();

    /**
     * @return The projections that apply to this query.
     */
    @NonNull
    List<Projection> getProjections();

    /**
     * Obtain the join type for the given association.
     * @param path The path
     * @return The join type for the association.
     */
    Optional<JoinPath> getJoinPath(String path);

    /**
     * Join on the given association.
     * @param path The join path
     * @param association The association
     * @param joinType The join type
     * @param alias The alias to use.
     * @return The query
     */
    @NonNull
    @Deprecated
    JoinPath join(String path, Association association, @NonNull Join.Type joinType, @Nullable String alias);

    /**
     * Join on the given association.
     * @param path The join path
     * @param joinType The join type
     * @param alias The alias to use.
     * @return The query
     */
    @NonNull
    default JoinPath join(String path, @NonNull Join.Type joinType, @Nullable String alias) {
        return join(path, joinType, alias);
    }

    /**
     * Join on the given association.
     * @param association The association, never null
     * @param joinType The join type
     * @return The query
     */
    @NonNull
    default JoinPath join(@NonNull Association association, @NonNull Join.Type joinType) {
        if (getPersistentEntity() != association.getOwner()) {
            throw new IllegalArgumentException("The association " + association + " must be owned by: " + getPersistentEntity());
        }
        return join(association.getName(), association, joinType, null);
    }

    /**
     * Join on the given association.
     * @param association The association, never null
     * @return The query
     */
    @NonNull
    default JoinPath join(@NonNull Association association) {
        return join(association.getName(), association, Join.Type.DEFAULT, null);
    }

    /**
     * @return The projection list.
     */
    @NonNull
    ProjectionList projections();

    /**
     * Adds the specified criterion instance to the query.
     *
     * @param criterion The criterion instance
     * @return This query
     */
    @NonNull
    QueryModel add(@NonNull Criterion criterion);

    /**
     * Limits the maximum result.
     * @param max The pageSize
     * @return This query
     */
    QueryModel max(int max);

    /**
     * Sets the offset.
     * @param offset The offset
     * @return This query
     */
    QueryModel offset(long offset);

    /**
     * The sort to apply.
     * @return The sort
     */
    default Sort getSort() {
        return Sort.unsorted();
    }

    /**
     * Apply the given sort.
     * @param sort The sort to apply
     * @return This query
     */
    @NonNull
    QueryModel sort(@NonNull Sort sort);

    /**
     * Creates a query from the given entity.
     *
     * @param entity The entity
     * @return The query
     */
    static @NonNull
    QueryModel from(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        return new DefaultQuery(entity);
    }

    /**
     * Get the pageSize results to return.
     * @return The pageSize results
     */
    int getMax();

    /**
     * Get the offset of the query.
     * @return The offset
     */
    long getOffset();

    /**
     * Lock the selected entities.
     */
    void forUpdate();

    /**
     * Whether to lock the selected entities.
     * @return true if the the selected entities should be locked
     */
    boolean isForUpdate();

    /**
     * Represents a criterion to be used in a criteria query.
     */
    interface Criterion {
    }

    /**
     * Restricts a property to be null.
     */
    class IsNull extends QueryModel.PropertyNameCriterion {
        /**
         * Default constructor.
         * @param name The property name
         */
        public IsNull(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be empty (such as a blank string).
     */
    class IsEmpty extends QueryModel.PropertyNameCriterion {
        /**
         * Default constructor.
         * @param name The property name
         */
        public IsEmpty(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be empty (such as a blank string).
     */
    class IsNotEmpty extends QueryModel.PropertyNameCriterion {
        /**
         * Default constructor.
         * @param name The property name
         */
        public IsNotEmpty(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be not null.
     */
    class IsNotNull extends QueryModel.PropertyNameCriterion {
        /**
         * Default constructor.
         * @param name The property name
         */
        public IsNotNull(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be true.
     */
    class IsTrue extends QueryModel.PropertyNameCriterion {
        /**
         * Default constructor.
         * @param name The property name
         */
        public IsTrue(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be false.
     */
    class IsFalse extends QueryModel.PropertyNameCriterion {
        /**
         * Default constructor.
         * @param name The property name
         */
        public IsFalse(String name) {
            super(name);
        }
    }

    /**
     * A Criterion that applies to a property.
     */
    class PropertyNameCriterion implements Criterion {
        protected String name;

        /**
         * Default constructor.
         * @param name The name of the property.
         */
        public PropertyNameCriterion(String name) {
            this.name = name;
        }

        /**
         * @return The name of the property
         */
        public String getProperty() {
            return name;
        }
    }

    /**
     * A Criterion that compares to properties.
     */
    class PropertyComparisonCriterion extends PropertyNameCriterion {
        final String otherProperty;

        /**
         * Default constructor.
         * @param property The property name
         * @param otherProperty The other property name
         */
        protected PropertyComparisonCriterion(String property, String otherProperty) {
            super(property);
            this.otherProperty = otherProperty;
        }

        /**
         * @return The other property
         */
        public String getOtherProperty() {
            return otherProperty;
        }
    }

    /**
     * A criterion for one property equaling another.
     */
    class EqualsProperty extends PropertyComparisonCriterion {
        /**
         * Default constructor.
         * @param property The property name
         * @param otherProperty The other property name
         */
        public EqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    /**
     * A criterion for one property not equaling another.
     */
    class NotEqualsProperty extends PropertyComparisonCriterion {
        /**
         * Default constructor.
         * @param property The property name
         * @param otherProperty The other property name
         */
        public NotEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    /**
     * A criterion for one property being greater than another.
     */
    class GreaterThanProperty extends PropertyComparisonCriterion {
        /**
         * Default constructor.
         * @param property The property name
         * @param otherProperty The other property name
         */
        public GreaterThanProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    /**
     * A criterion for one property being greater than or equal to another.
     */
    class GreaterThanEqualsProperty extends PropertyComparisonCriterion {
        /**
         * Default constructor.
         * @param property The property name
         * @param otherProperty The other property name
         */
        public GreaterThanEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    /**
     * A criterion for one property being less than another.
     */
    class LessThanProperty extends PropertyComparisonCriterion {
        /**
         * Default constructor.
         * @param property The property name
         * @param otherProperty The other property name
         */
        public LessThanProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    /**
     * A criterion for one property being less than or equal to another.
     */
    class LessThanEqualsProperty extends PropertyComparisonCriterion {
        /**
         * Default constructor.
         * @param property The property name
         * @param otherProperty The other property name
         */
        public LessThanEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    /**
     * Criterion that applies to a property and value.
     */
    class PropertyCriterion extends PropertyNameCriterion {

        protected Object value;
        private boolean ignoreCase = false;

        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public PropertyCriterion(String name, Object value) {
            super(name);
            this.value = value;
        }

        /**
         * @return The value
         */
        public Object getValue() {
            return value;
        }

        /**
         * Sets the value.
         * @param v The value to set
         */
        public void setValue(Object v) {
            this.value = v;
        }

        /**
         * @return Whether to ignore case,
         */
        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        /**
         * Sets whether to ignore case.
         * @param ignoreCase True if case should be ignored
         * @return This criterion
         */
        public PropertyCriterion ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }
    }

    /**
     * Used to differentiate criterion that require a subquery.
     */
    class SubqueryCriterion extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public SubqueryCriterion(String name, QueryModel value) {
            super(name, value);
        }

        @Override
        public QueryModel getValue() {
            return (QueryModel) super.getValue();
        }
    }

    /**
     * Restricts a value to be equal to all the given values.
     */
    class EqualsAll extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public EqualsAll(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be not equal to all the given values.
     */
    class NotEqualsAll extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public NotEqualsAll(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than all the given values.
     */
    class GreaterThanAll extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public GreaterThanAll(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than some of the given values.
     */
    class GreaterThanSome extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public GreaterThanSome(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than some of the given values.
     */
    class GreaterThanEqualsSome extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public GreaterThanEqualsSome(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than some of the given values.
     */
    class LessThanSome extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public LessThanSome(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than some of the given values.
     */
    class LessThanEqualsSome extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public LessThanEqualsSome(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than all the given values.
     */
    class LessThanAll extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public LessThanAll(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than or equal to all the given values.
     */
    class GreaterThanEqualsAll extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public GreaterThanEqualsAll(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than or equal to all the given values.
     */
    class LessThanEqualsAll extends SubqueryCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The criterion value
         */
        public LessThanEqualsAll(String name, QueryModel value) {
            super(name, value);
        }
    }

    /**
     * A criterion that restricts the results based on equality.
     */
    class Equals extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param parameter The parameter
         */
        public Equals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * Size equals criterion.
     */
    class SizeEquals extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param parameter The parameter
         */
        public SizeEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * Size not equals criterion.
     */
    class SizeNotEquals extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param parameter The parameter
         */
        public SizeNotEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * Size greater than criterion.
     */
    class SizeGreaterThan extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param parameter The parameter
         */
        public SizeGreaterThan(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * Size greater than equals criterion.
     */
    class SizeGreaterThanEquals extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param parameter The parameter
         */
        public SizeGreaterThanEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * Size less than equals criterion.
     */
    class SizeLessThanEquals extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param parameter The parameter
         */
        public SizeLessThanEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * Size less than criterion.
     */
    class SizeLessThan extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param parameter The parameter
         */
        public SizeLessThan(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * A criterion that restricts the results based on the equality of the identifier.
     */
    class IdEquals extends PropertyCriterion {

        private static final String ID = "id";

        /**
         * Default constructor.
         * @param value The parameter
         */
        public IdEquals(QueryParameter value) {
            super(ID, value);
        }
    }

    /**
     * A criterion that restricts the results based on equality.
     */
    class NotEquals extends PropertyCriterion {

        /**
         * Default constructor.
         * @param name The property name
         * @param value The parameter
         */
        public NotEquals(String name, QueryParameter value) {
            super(name, value);
        }

    }

    /**
     * Criterion used to restrict the results based on a list of values.
     */
    class In extends PropertyCriterion {
        private QueryModel subquery;

        /**
         * Constructor for an individual parameter.
         * @param name The name
         * @param parameter The parameter
         */
        public In(String name, QueryParameter parameter) {
            super(name, parameter);
        }

        /**
         * Constructor for a subquery.
         * @param name The name
         * @param subquery The subquery
         */
        public In(String name, QueryModel subquery) {
            super(name, subquery);
            this.subquery = subquery;
        }

        /**
         * @return The name
         */
        public String getName() {
            return getProperty();
        }

        /**
         * @return The subquery
         */
        public @Nullable
        QueryModel getSubquery() {
            return subquery;
        }
    }

    /**
     * Criterion used to restrict the results based on a list of values.
     */
    class NotIn extends SubqueryCriterion {
        private QueryModel subquery;

        /**
         * Constructor for a subquery.
         * @param name The name
         * @param subquery The subquery
         */
        public NotIn(String name, QueryModel subquery) {
            super(name, subquery);
            this.subquery = subquery;
        }

        /**
         * @return The name
         */
        public String getName() {
            return getProperty();
        }

        /**
         * @return The subquery
         */
        public QueryModel getSubquery() {
            return subquery;
        }
    }

    /**
     * Used for exists subquery.
     */
    class Exists implements Criterion {
        private QueryModel subquery;

        /**
         * Constructor for a subquery.
         * @param subquery The subquery
         */
        public Exists(QueryModel subquery) {
            this.subquery = subquery;
        }

        /**
         * @return The subquery
         */
        public QueryModel getSubquery() {
            return subquery;
        }
    }

    /**
     * Used for exists subquery.
     */
    class NotExists implements Criterion {
        private QueryModel subquery;

        /**
         * Constructor for a subquery.
         * @param subquery The subquery
         */
        public NotExists(QueryModel subquery) {
            this.subquery = subquery;
        }

        /**
         * @return The subquery
         */
        public QueryModel getSubquery() {
            return subquery;
        }
    }

    /**
     * Used to restrict a value to be greater than the given value.
     */
    class GreaterThan extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The parameter
         */
        public GreaterThan(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value.
     */
    class GreaterThanEquals extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The parameter
         */
        public GreaterThanEquals(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Used to restrict a value to be less than the given value.
     */
    class LessThan extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The parameter
         */
        public LessThan(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Used to restrict a value to be less than the given value.
     */
    class LessThanEquals extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param value The parameter
         */
        public LessThanEquals(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Criterion used to restrict the result to be between values (range query).
     */
    class Between extends PropertyCriterion {
        private String property;
        private QueryParameter from;
        private QueryParameter to;

        /**
         * Default constructor.
         * @param property The property name
         * @param from The from parameter
         * @param to The to parameter
         */
        public Between(String property, QueryParameter from, QueryParameter to) {
            super(property, from);
            this.property = property;
            this.from = from;
            this.to = to;
        }

        @Override
        public String getProperty() {
            return property;
        }

        /**
         * @return The from parameter
         */
        public QueryParameter getFrom() {
            return from;
        }

        /**
         * @return The to parameter
         */
        public QueryParameter getTo() {
            return to;
        }
    }

    /**
     * Criterion used to restrict the results based on a pattern (likeness).
     */
    class Like extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param expression The parameter
         */
        public Like(String name, QueryParameter expression) {
            super(name, expression);
        }
    }

    /**
     * Criterion used to restrict the results based on starting with a given value.
     */
    class StartsWith extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param expression The parameter
         */
        public StartsWith(String name, QueryParameter expression) {
            super(name, expression);
        }
    }

    /**
     * Criterion used to restrict the results based on a result containing the given value.
     */
    class Contains extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param expression The parameter
         */
        public Contains(String name, QueryParameter expression) {
            super(name, expression);
        }
    }

    /**
     * Criterion used to restrict the results based on starting with a given value.
     */
    class EndsWith extends PropertyCriterion {
        /**
         * Default constructor.
         * @param name The property name
         * @param expression The parameter
         */
        public EndsWith(String name, QueryParameter expression) {
            super(name, expression);
        }
    }

    /**
     * Criterion used to restrict the results based on a pattern (likeness).
     */
    class ILike extends Like {
        /**
         * Default constructor.
         * @param name The property name
         * @param expression The parameter
         */
        public ILike(String name, QueryParameter expression) {
            super(name, expression);
        }
    }

    /**
     * Criterion used to restrict the results based on a regular expression pattern.
     */
    class RLike extends Like {
        /**
         * Default constructor.
         * @param name The property name
         * @param expression The parameter
         */
        public RLike(String name, QueryParameter expression) {
            super(name, expression);
        }

    }

    /**
     * base class for a junction (AND or OR or NOT).
     */
    abstract class Junction implements Criterion {
        private List<Criterion> criteria = new ArrayList<Criterion>();

        /**
         * Default constructor.
         */
        protected Junction() {
        }

        /**
         * Creates a junction for a list of citeria.
         * @param criteria the criteria
         */
        public Junction(List<Criterion> criteria) {
            this.criteria = criteria;
        }

        /**
         * Adds an additional criterion.
         * @param c The criterion
         * @return This junction
         */
        public Junction add(Criterion c) {
            if (c != null) {
                criteria.add(c);
            }
            return this;
        }

        /**
         * @return The Criterion for the junction.
         */
        public List<Criterion> getCriteria() {
            return criteria;
        }

        /**
         * @return Whether the junction is empty
         */
        public boolean isEmpty() {
            return criteria.isEmpty();
        }
    }

    /**
     * A Criterion used to combine to criterion in a logical AND.
     */
    class Conjunction extends Junction {
        /**
         * Default constructor.
         */
        public Conjunction() {
        }
    }

    /**
     * A Criterion used to combine to criterion in a logical OR.
     */
    class Disjunction extends Junction {
        /**
         * Default constructor.
         */
        public Disjunction() {
        }
    }

    /**
     * A criterion used to negate several other criterion.
     */
    class Negation extends Junction {

    }

    /**
     * A projection.
     */
    class Projection {
    }

    /**
     * A projection used to obtain the identifier of an object.
     */
    class IdProjection extends Projection {

    }

    /**
     * Used to count the results of a query.
     */
    class CountProjection extends Projection {

    }

    /**
     * Distinct result projection.
     */
    class DistinctProjection extends Projection {

    }

    /**
     * A projection that obtains the value of a property of an entity.
     */
    class PropertyProjection extends Projection {
        private String propertyName;
        private String alias;

        /**
         * Default constructor.
         * @param propertyName The property name
         */
        public PropertyProjection(String propertyName) {
            this.propertyName = propertyName;
        }

        /**
         * @return The property name
         */
        public String getPropertyName() {
            return propertyName;
        }

        /**
         * Include an alias that is the same as the property name.
         * @return This property
         */
        public PropertyProjection aliased() {
            this.alias = propertyName;
            return this;
        }

        /**
         * @return The alias to use for the projection.
         */
        public Optional<String> getAlias() {
            return Optional.ofNullable(alias);
        }
    }

    /**
     * Projection to return distinct property names.
     */
    class DistinctPropertyProjection extends PropertyProjection {
        /**
         * Default constructor.
         * @param propertyName The property name
         */
        public DistinctPropertyProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Projection to count distinct property names.
     */
    class CountDistinctProjection extends PropertyProjection {
        /**
         * Default constructor.
         * @param property The property name
         */
        public CountDistinctProjection(String property) {
            super(property);
        }
    }

    /**
     * Group by property projection.
     */
    class GroupPropertyProjection extends PropertyProjection {
        /**
         * Default constructor.
         * @param property The property name
         */
        public GroupPropertyProjection(String property) {
            super(property);
        }
    }

    /**
     * Computes the average value of a property.
     */
    class AvgProjection extends PropertyProjection {
        /**
         * Default constructor.
         * @param propertyName The property name
         */
        public AvgProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Computes the pageSize value of a property.
     */
    class MaxProjection extends PropertyProjection {
        /**
         * Default constructor.
         * @param propertyName The property name
         */
        public MaxProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Computes the min value of a property.
     */
    class MinProjection extends PropertyProjection {
        /**
         * Default constructor.
         * @param propertyName The property name
         */
        public MinProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Computes the sum of a property.
     */
    class SumProjection extends PropertyProjection {
        /**
         * Default constructor.
         * @param propertyName The property name
         */
        public SumProjection(String propertyName) {
            super(propertyName);
        }
    }

}
