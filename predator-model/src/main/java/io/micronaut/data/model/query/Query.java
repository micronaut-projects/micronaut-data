package io.micronaut.data.model.query;

import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.factory.Projections;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface Query extends Criteria {

    /**
     * @return The entity the criteria applies to
     */
    @Nonnull
    PersistentEntity getPersistentEntity();

    /**
     * @return The criteria for this query
     */
    @Nonnull
    Query.Junction getCriteria();

    @Nonnull
    List<Projection> getProjections();

    /**
     * Adds the specified criterion instance to the query
     *
     * @param criterion The criterion instance
     */
    @Nonnull Query add(@Nonnull Criterion criterion);

    /**
     * Creates a query from the given entity.
     * @param entity The entity
     */
    static @Nonnull Query from(@Nonnull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        return new DefaultQuery(entity);
    }

    /**
     * Represents a criterion to be used in a criteria query
     */
    interface Criterion {}

    /**
     * The ordering of results.
     */
    class Order {
        private Direction direction = Direction.ASC;
        private String property;
        private boolean ignoreCase = false;

        public Order(String property) {
            this.property = property;
        }

        public Order(String property, Direction direction) {
            this.direction = direction;
            this.property = property;
        }

        /**
         * Whether to ignore the case for this order definition
         *
         * @return This order instance
         */
        public Order ignoreCase() {
            this.ignoreCase = true;
            return this;
        }

        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        /**
         * @return The direction order by
         */
        public Direction getDirection() {
            return direction;
        }

        /**
         * @return The property name to order by
         */
        public String getProperty() {
            return property;
        }

        /**
         * Creates a new order for the given property in descending order
         *
         * @param property The property
         * @return The order instance
         */
        public static Order desc(String property) {
            return new Order(property, Direction.DESC);
        }

        /**
         * Creates a new order for the given property in ascending order
         *
         * @param property The property
         * @return The order instance
         */
        public static Order asc(String property) {
            return new Order(property, Direction.ASC);
        }

        /**
         * Represents the direction of the ordering
         */
        public static enum Direction {
            ASC, DESC
        }
    }

    /**
     * Restricts a property to be null
     */
    class IsNull extends Query.PropertyNameCriterion {
        public IsNull(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be empty (such as a blank string)
     */
    class IsEmpty extends Query.PropertyNameCriterion {
        public IsEmpty(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be empty (such as a blank string)
     */
    class IsNotEmpty extends Query.PropertyNameCriterion {
        public IsNotEmpty(String name) {
            super(name);
        }
    }

    /**
     * Restricts a property to be not null
     */
    class IsNotNull extends Query.PropertyNameCriterion {
        public IsNotNull(String name) {
            super(name);
        }
    }

    /**
     * A Criterion that applies to a property
     */
    class PropertyNameCriterion implements Criterion {
        protected String name;

        public PropertyNameCriterion(String name) {
            this.name = name;
        }

        public String getProperty() {
            return name;
        }
    }

    /**
     * A Criterion that compares to properties
     */
    class PropertyComparisonCriterion extends PropertyNameCriterion{
        protected String otherProperty;

        public PropertyComparisonCriterion(String property, String otherProperty) {
            super(property);
            this.otherProperty = otherProperty;
        }

        public String getOtherProperty() {
            return otherProperty;
        }
    }

    class EqualsProperty extends PropertyComparisonCriterion {
        public EqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    class NotEqualsProperty extends PropertyComparisonCriterion {
        public NotEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    class GreaterThanProperty extends PropertyComparisonCriterion {
        public GreaterThanProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    class GreaterThanEqualsProperty extends PropertyComparisonCriterion {
        public GreaterThanEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    class LessThanProperty extends PropertyComparisonCriterion {
        public LessThanProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    class LessThanEqualsProperty extends PropertyComparisonCriterion {
        public LessThanEqualsProperty(String property, String otherProperty) {
            super(property, otherProperty);
        }
    }

    /**
     * Criterion that applies to a property and value
     */
    class PropertyCriterion extends PropertyNameCriterion {

        protected Object value;

        public PropertyCriterion(String name, Object value) {
            super(name);
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object v) {
            this.value = v;
        }
    }

    /**
     * Used to differentiate criterion that require a subquery
     */
    class SubqueryCriterion extends PropertyCriterion {
        public SubqueryCriterion(String name, Query value) {
            super(name, value);
        }

        @Override
        public Query getValue() {
            return (Query) super.getValue();
        }
    }

    /**
     * Restricts a value to be equal to all the given values
     */
    class EqualsAll extends SubqueryCriterion{
        public EqualsAll(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be not equal to all the given values
     */
    class NotEqualsAll extends SubqueryCriterion{
        public NotEqualsAll(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than all the given values
     */
    class GreaterThanAll extends SubqueryCriterion {
        public GreaterThanAll(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than some of the given values
     */
    class GreaterThanSome extends SubqueryCriterion{
        public GreaterThanSome(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than some of the given values
     */
    class GreaterThanEqualsSome extends SubqueryCriterion{
        public GreaterThanEqualsSome(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than some of the given values
     */
    class LessThanSome extends SubqueryCriterion{
        public LessThanSome(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than some of the given values
     */
    class LessThanEqualsSome extends SubqueryCriterion {
        public LessThanEqualsSome(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than all the given values
     */
    class LessThanAll extends SubqueryCriterion {
        public LessThanAll(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be greater than or equal to all the given values
     */
    class GreaterThanEqualsAll extends SubqueryCriterion {
        public GreaterThanEqualsAll(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * Restricts a value to be less than or equal to all the given values
     */
    class LessThanEqualsAll extends SubqueryCriterion{
        public LessThanEqualsAll(String name, Query value) {
            super(name, value);
        }
    }

    /**
     * A criterion that restricts the results based on equality
     */
    class Equals extends PropertyCriterion {
        public Equals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    class SizeEquals extends PropertyCriterion {
        public SizeEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    class SizeNotEquals extends PropertyCriterion{
        public SizeNotEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    class SizeGreaterThan extends PropertyCriterion{
        public SizeGreaterThan(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    class SizeGreaterThanEquals extends PropertyCriterion{
        public SizeGreaterThanEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    class SizeLessThanEquals extends PropertyCriterion{
        public SizeLessThanEquals(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    class SizeLessThan extends PropertyCriterion{
        public SizeLessThan(String name, QueryParameter parameter) {
            super(name, parameter);
        }
    }

    /**
     * A criterion that restricts the results based on the equality of the identifier
     */
    class IdEquals extends PropertyCriterion {

        private static final String ID = "id";

        public IdEquals(QueryParameter value) {
            super(ID, value);
        }

    }

    /**
     * A criterion that restricts the results based on equality
     */
    class NotEquals extends PropertyCriterion {

        public NotEquals(String name, QueryParameter value) {
            super(name, value);
        }

    }

    /**
     * Criterion used to restrict the results based on a list of values
     */
    class In extends PropertyCriterion {
        private Query subquery;

        public In(String name, QueryParameter parameter) {
            super(name, parameter);
        }

        public In(String name, Query subquery) {
            super(name, subquery);
            this.subquery = subquery;
        }

        public String getName() {
            return getProperty();
        }

        public Query getSubquery() {
            return subquery;
        }
    }

    /**
     * Criterion used to restrict the results based on a list of values
     */
    class NotIn extends SubqueryCriterion {
        private Query subquery;


        public NotIn(String name, Query subquery) {
            super(name, subquery);
            this.subquery = subquery;
        }

        public String getName() {
            return getProperty();
        }

        public Query getSubquery() {
            return subquery;
        }
    }

    /**
     * Used for exists subquery
     */
    class Exists implements Criterion {
        private Query subquery;

        public Exists(Query subquery) {
            this.subquery = subquery;
        }

        public Query getSubquery() {
            return subquery;
        }
    }

    /**
     * Used for exists subquery
     */
    class NotExists implements Criterion {
        private Query subquery;

        public NotExists(Query subquery) {
            this.subquery = subquery;
        }

        public Query getSubquery() {
            return subquery;
        }
    }

    /**
     * Used to restrict a value to be greater than the given value
     */
    class GreaterThan extends PropertyCriterion {
        public GreaterThan(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     */
    class GreaterThanEquals extends PropertyCriterion {
        public GreaterThanEquals(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Used to restrict a value to be less than the given value
     */
    class LessThan extends PropertyCriterion {
        public LessThan(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Used to restrict a value to be less than the given value
     */
    class LessThanEquals extends PropertyCriterion {
        public LessThanEquals(String name, QueryParameter value) {
            super(name, value);
        }
    }

    /**
     * Criterion used to restrict the result to be between values (range query)
     */
    class Between extends PropertyCriterion {
        private String property;
        private QueryParameter from;
        private QueryParameter to;

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

        public QueryParameter getFrom() {
            return from;
        }

        public QueryParameter getTo() {
            return to;
        }
    }

    /**
     * Criterion used to restrict the results based on a pattern (likeness)
     */
    class Like extends PropertyCriterion {
        public Like(String name, QueryParameter expression) {
            super(name, expression);
        }
    }

    /**
     * Criterion used to restrict the results based on a pattern (likeness)
     */
    class ILike extends Like {
        public ILike(String name, QueryParameter expression) {
            super(name, expression);
        }
    }

    /**
     * Criterion used to restrict the results based on a regular expression pattern
     */
    class RLike extends Like {
        public RLike(String name, QueryParameter expression) {
            super(name, expression);
        }

    }

    abstract class Junction implements Criterion {
        private List<Criterion> criteria = new ArrayList<Criterion>();

        protected Junction() {
        }

        public Junction(List<Criterion> criteria) {
            this.criteria = criteria;
        }

        public Junction add(Criterion c) {
            if (c != null) {
                criteria.add(c);
            }
            return this;
        }

        public List<Criterion> getCriteria() {
            return criteria;
        }

        public boolean isEmpty() {
            return criteria.isEmpty();
        }
    }

    /**
     * A Criterion used to combine to criterion in a logical AND
     */
    class Conjunction extends Junction {
        public Conjunction() {
        }

        public Conjunction(List<Criterion> criteria) {
            super(criteria);
        }
    }

    /**
     * A Criterion used to combine to criterion in a logical OR
     */
    class Disjunction extends Junction {
        public Disjunction() {
        }

        public Disjunction(List<Criterion> criteria) {
            super(criteria);
        }
    }

    /**
     * A criterion used to negate several other criterion
     */
    class Negation extends Junction {}

    /**
     * A projection
     */
    class Projection {}

    /**
     * A projection used to obtain the identifier of an object
     */
    class IdProjection extends Projection {}

    /**
     * Used to count the results of a query
     */
    class CountProjection extends Projection {}

    class DistinctProjection extends Projection {}

    /**
     * A projection that obtains the value of a property of an entity
     */
    class PropertyProjection extends Projection {
        private String propertyName;

        public PropertyProjection(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getPropertyName() {
            return propertyName;
        }
    }

    class DistinctPropertyProjection extends PropertyProjection{
        public DistinctPropertyProjection(String propertyName) {
            super(propertyName);
        }
    }

    class CountDistinctProjection extends PropertyProjection{
        public CountDistinctProjection(String property) {
            super(property);
        }
    }

    class GroupPropertyProjection extends PropertyProjection{
        public GroupPropertyProjection(String property) {
            super(property);
        }
    }

    /**
     * Computes the average value of a property
     */
    class AvgProjection extends PropertyProjection {
        public AvgProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Computes the max value of a property
     */
    class MaxProjection extends PropertyProjection {
        public MaxProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Computes the min value of a property
     */
    class MinProjection extends PropertyProjection {
        public MinProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * Computes the sum of a property
     */
    class SumProjection extends PropertyProjection {
        public SumProjection(String propertyName) {
            super(propertyName);
        }
    }

    /**
     * A list of projections
     */
    class ProjectionList implements io.micronaut.data.model.query.ProjectionList {

        private List<Projection> projections = new ArrayList();

        public List<Projection> getProjectionList() {
            return Collections.unmodifiableList(projections);
        }

        public io.micronaut.data.model.query.ProjectionList add(Projection p) {
            projections.add(p);
            return this;
        }

        public io.micronaut.data.model.query.ProjectionList id() {
            add(Projections.id());
            return this;
        }

        public io.micronaut.data.model.query.ProjectionList count() {
            add(Projections.count());
            return this;
        }

        public io.micronaut.data.model.query.ProjectionList countDistinct(String property) {
            add(Projections.countDistinct(property));
            return this;
        }

        @Override
        public io.micronaut.data.model.query.ProjectionList groupProperty(String property) {
            add(Projections.groupProperty(property));
            return this;
        }

        public boolean isEmpty() {
            return projections.isEmpty();
        }

        public io.micronaut.data.model.query.ProjectionList distinct() {
            return this;
        }

        public io.micronaut.data.model.query.ProjectionList distinct(String property) {
            add(Projections.distinct(property));
            return this;
        }

        public io.micronaut.data.model.query.ProjectionList rowCount() {
            return count();
        }

        /**
         * A projection that obtains the value of a property of an entity
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        public io.micronaut.data.model.query.ProjectionList property(String name) {
            add(Projections.property(name));
            return this;
        }

        /**
         * Computes the sum of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        public io.micronaut.data.model.query.ProjectionList sum(String name) {
            add(Projections.sum(name));
            return this;
        }

        /**
         * Computes the min value of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        public io.micronaut.data.model.query.ProjectionList min(String name) {
            add(Projections.min(name));
            return this;
        }

        /**
         * Computes the max value of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        public io.micronaut.data.model.query.ProjectionList max(String name) {
            add(Projections.max(name));
            return this;
        }

        /**
         * Computes the average value of a property
         *
         * @param name The name of the property
         * @return The PropertyProjection instance
         */
        public io.micronaut.data.model.query.ProjectionList avg(String name) {
            add(Projections.avg(name));
            return this;
        }
    }
}
