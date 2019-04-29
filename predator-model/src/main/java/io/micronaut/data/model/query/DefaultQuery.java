package io.micronaut.data.model.query;

import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.factory.Restrictions;

import javax.annotation.Nonnull;
import javax.persistence.FetchType;
import javax.persistence.LockModeType;
import javax.persistence.criteria.JoinType;
import java.util.*;

/**
 * Models a query that can be executed against a data store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultQuery implements Query {

    private final PersistentEntity entity;

    private Query.Junction criteria = new Query.Conjunction();
    private Query.ProjectionList projections = new Query.ProjectionList();
    private int max = -1;
    private int offset = 0;
    private List<Query.Order> orderBy = new ArrayList<>();
    private Map<String, FetchType> fetchStrategies = new HashMap<>();
    private Map<String, JoinType> joinTypes = new HashMap<>();
    private Boolean queryCache;
    private LockModeType lockResult;

    protected DefaultQuery(@Nonnull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        this.entity = entity;
    }

    /**
     * Creates an association query
     *
     * @param associationName The assocation name
     * @return The Query instance
     */
    public AssociationQuery createQuery(String associationName) {
        final PersistentProperty property = entity.getPropertyByName(associationName);
        if (!(property instanceof Association)) {
            throw new IllegalArgumentException("Cannot query association [" +
                    associationName + "] of class [" + entity +
                    "]. The specified property is not an association.");
        }

        Association association = (Association) property;
        return new AssociationQuery(association);
    }

    @Nonnull
    @Override
    public PersistentEntity getPersistentEntity() {
        return entity;
    }

    /**
     * @return The criteria defined by this query
     */
    public Query.Junction getCriteria() {
        return criteria;
    }

    @Nonnull
    @Override
    public List<Projection> getProjections() {
        return projections.getProjectionList();
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    public DefaultQuery join(String property) {
        fetchStrategies.put(property, FetchType.EAGER);
        return this;
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    public DefaultQuery join(String property, JoinType joinType) {
        fetchStrategies.put(property, FetchType.EAGER);
        joinTypes.put(property, joinType);
        return this;
    }

    /**
     * Specifies whether a select (lazy) query should be used (if join queries are supported by the underlying datastore)
     *
     * @param property The property
     * @return The query
     */
    public DefaultQuery select(String property) {
        fetchStrategies.put(property, FetchType.LAZY);
        return this;
    }

    /**
     * Specifies whether the query results should be cached (if supported by the underlying datastore)
     *
     * @param cache True if caching should be enabled
     * @return The query
     */
    public DefaultQuery cache(boolean cache) {
        queryCache = cache;
        return this;
    }

    @Nonnull
    @Override
    public Criteria readOnly(boolean readOnly) {
        return null;
    }

    /**
     * Specifies whether the query should obtain a pessimistic lock
     *
     * @param lock True if a lock should be obtained
     * @return The query
     */
    public DefaultQuery lock(boolean lock) {
        lockResult = LockModeType.PESSIMISTIC_WRITE;
        return this;
    }

    /**
     * Specifies whether the query should obtain a pessimistic lock
     *
     * @param lock True if a lock should be obtained
     * @return The query
     */
    public DefaultQuery lock(LockModeType lock) {
        lockResult = lock;
        return this;
    }

    /**
     * @return The projections for this query.
     */
    public Query.ProjectionList projections() {
        return projections;
    }

    /**
     * Adds the specified criterion instance to the query
     *
     * @param criterion The criterion instance
     */
    @Override
    public @Nonnull Query add(@Nonnull Query.Criterion criterion) {
        ArgumentUtils.requireNonNull("criterion", criterion);
        Query.Junction currentJunction = criteria;
        add(currentJunction, criterion);
        return this;
    }

    /**
     * Adds the specified criterion instance to the given junction
     *
     * @param currentJunction The junction to add the criterion to
     * @param criterion The criterion instance
     */
    private void add(Query.Junction currentJunction, Query.Criterion criterion) {
        addToJunction(currentJunction, criterion);
    }

    /**
     * @return The PersistentEntity being query
     */
    public PersistentEntity getEntity() {
        return entity;
    }

    /**
     * Creates a disjunction (OR) query
     * @return The Junction instance
     */
    public Query.Junction disjunction() {
        Query.Junction currentJunction = criteria;
        return disjunction(currentJunction);
    }

    /**
     * Creates a conjunction (AND) query
     * @return The Junction instance
     */
    public Query.Junction conjunction() {
        Query.Junction currentJunction = criteria;
        return conjunction(currentJunction);
    }

    /**
     * Creates a negation of several criterion
     * @return The negation
     */
    public Query.Junction negation() {
        Query.Junction currentJunction = criteria;
        return negation(currentJunction);
    }

    private Query.Junction negation(Query.Junction currentJunction) {
        Query.Negation dis = new Query.Negation();
        currentJunction.add(dis);
        return dis;
    }

    /**
     * Defines the maximum number of results to return
     * @param max The max results
     * @return This query instance
     */
    public DefaultQuery max(int max) {
        this.max = max;
        return this;
    }

    /**
     * Defines the maximum number of results to return
     * @param max The max results
     * @return This query instance
     */
    public DefaultQuery maxResults(int max) {
        return max(max);
    }

    /**
     * Defines the offset (the first result index) of the query
     * @param offset The offset
     * @return This query instance
     */
    public DefaultQuery offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Defines the offset (the first result index) of the query
     * @param offset The offset
     * @return This query instance
     */
    public DefaultQuery firstResult(int offset) {
        return offset(offset);
    }

    /**
     * Specifies the order of results
     * @param order The order object
     * @return The Query instance
     */
    public DefaultQuery order(Query.Order order) {
        if (order != null) {
            orderBy.add(order);
        }
        return this;
    }

    /**
     * Gets the Order entries for this query
     * @return The order entries
     */
    public List<Query.Order> getOrderBy() {
        return orderBy;
    }

    /**
     * Restricts the results by the given properties value
     *
     * @param property The name of the property
     * @param parameter The parameter that provides the value
     * @return This query instance
     */
    @Override
    public @Nonnull
    DefaultQuery eq(@Nonnull String property, @Nonnull QueryParameter parameter) {
        criteria.add(Restrictions.eq(property,parameter));
        return this;
    }

    /**
     * Shortcut to restrict the query to multiple given property values
     *
     * @param values The values
     * @return This query instance
     */
    public @Nonnull
    DefaultQuery allEq(@Nonnull Map<String, QueryParameter> values) {
        Query.Junction conjunction = conjunction();
        for (String property : values.keySet()) {
            QueryParameter value = values.get(property);
            conjunction.add(Restrictions.eq(property, value));
        }
        return this;
    }

    @Nonnull
    @Override
    public Criteria eqAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria gtAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria ltAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria geAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria leAll(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria gtSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria geSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria ltSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria leSome(@Nonnull String propertyName, @Nonnull Criteria propertyValue) {
        return null;
    }

    @Nonnull
    @Override
    public Criteria idEquals(QueryParameter parameter) {
        return null;
    }

    /**
     * Used to restrict a value to be empty (such as a blank string or an empty collection)
     *
     * @param property The property name
     */
    @Override
    public @Nonnull
    DefaultQuery isEmpty(@Nonnull String property) {
        criteria.add(Restrictions.isEmpty(property));
        return this;
    }

    /**
     * Used to restrict a value to be not empty (such as a blank string or an empty collection)
     *
     * @param property The property name
     */
    @Override
    public @Nonnull
    DefaultQuery isNotEmpty(@Nonnull String property) {
        criteria.add(Restrictions.isNotEmpty(property));
        return this;
    }

    /**
     * Used to restrict a property to be null
     *
     * @param property The property name
     */
    @Override
    public @Nonnull
    DefaultQuery isNull(@Nonnull String property) {
        criteria.add(Restrictions.isNull(property));
        return this;
    }

    /**
     * Used to restrict a property to be not null
     *
     * @param property The property name
     */
    @Override
    public @Nonnull
    DefaultQuery isNotNull(@Nonnull String property) {
        criteria.add(Restrictions.isNotNull(property));
        return this;
    }

    /**
     * Restricts the results by the given properties value
     *
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public @Nonnull
    DefaultQuery idEq(@Nonnull QueryParameter value) {
        criteria.add(Restrictions.idEq(value));
        return this;
    }

    @Nonnull
    @Override
    public Criteria ne(@Nonnull String propertyName, @Nonnull QueryParameter parameter) {
        criteria.add(Restrictions.ne(propertyName, parameter));
        return this;
    }

    /**
     * Used to restrict a value to be greater than the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public @Nonnull
    DefaultQuery gt(@Nonnull String property, @Nonnull QueryParameter value) {
        criteria.add(Restrictions.gt(property, value));
        return this;
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public DefaultQuery gte(String property, QueryParameter value) {
        criteria.add(Restrictions.gte(property, value));
        return this;
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public DefaultQuery lte(String property, QueryParameter value) {
        criteria.add(Restrictions.lte(property, value));
        return this;
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public DefaultQuery ge(String property, QueryParameter value) {
        return gte(property, value);
    }

    /**
     * Used to restrict a value to be less than or equal to the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public DefaultQuery le(String property, QueryParameter value) {
        return lte(property, value);
    }

    /**
     * Used to restrict a value to be less than the given value
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public DefaultQuery lt(String property, QueryParameter value) {
        criteria.add(Restrictions.lt(property, value));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery like(@Nonnull String propertyName, @Nonnull QueryParameter parameter) {
        criteria.add(Restrictions.like(propertyName, parameter));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery ilike(@Nonnull String propertyName, @Nonnull QueryParameter parameter) {
        criteria.add(Restrictions.ilike(propertyName, parameter));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery rlike(@Nonnull String propertyName, @Nonnull QueryParameter parameter) {
        criteria.add(Restrictions.rlike(propertyName, parameter));
        return this;
    }

    @Nonnull
    @Override
    public Criteria and(@Nonnull Criteria other) {
        // TODO
        return this;
    }

    @Nonnull
    @Override
    public Criteria or(@Nonnull Criteria other) {
        // TODO
        return this;
    }

    @Nonnull
    @Override
    public Criteria not(@Nonnull Criteria other) {
        // TODO
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery inList(@Nonnull String propertyName, @Nonnull Query subquery) {
        criteria.add(Restrictions.in(propertyName, subquery));
        return this;
    }

    /**
     * Restricts the results by the given property values
     *
     * @param property The name of the property
     * @param values The values to restrict by
     * @return This query instance
     */
    @Override
    public DefaultQuery inList(String property, QueryParameter values) {
        criteria.add(Restrictions.in(property, values));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery notIn(@Nonnull String propertyName, @Nonnull Query subquery) {
        criteria.add(Restrictions.notIn(propertyName, subquery));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery order(@Nonnull String propertyName) {
        // TODO
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery order(@Nonnull String propertyName, @Nonnull String direction) {
        // TODO
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery sizeEq(@Nonnull String propertyName, @Nonnull QueryParameter size) {
        criteria.add(Restrictions.sizeEq(propertyName, size));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery sizeGt(@Nonnull String propertyName, @Nonnull QueryParameter size) {
        criteria.add(Restrictions.sizeGt(propertyName, size));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery sizeGe(@Nonnull String propertyName, @Nonnull QueryParameter size) {
        criteria.add(Restrictions.sizeGe(propertyName, size));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery sizeLe(@Nonnull String propertyName, @Nonnull QueryParameter size) {
        criteria.add(Restrictions.sizeLe(propertyName, size));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery sizeLt(@Nonnull String propertyName, @Nonnull QueryParameter size) {
        criteria.add(Restrictions.sizeLt(propertyName, size));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery sizeNe(@Nonnull String propertyName, @Nonnull QueryParameter size) {
        criteria.add(Restrictions.sizeNe(propertyName, size));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery eqProperty(@Nonnull String propertyName, @Nonnull String otherPropertyName) {
        criteria.add(Restrictions.eqProperty(propertyName, otherPropertyName));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery neProperty(@Nonnull String propertyName, @Nonnull String otherPropertyName) {
        criteria.add(Restrictions.neProperty(propertyName, otherPropertyName));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery gtProperty(@Nonnull String propertyName, @Nonnull String otherPropertyName) {
        criteria.add(Restrictions.gtProperty(propertyName, otherPropertyName));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery geProperty(@Nonnull String propertyName, @Nonnull String otherPropertyName) {
        criteria.add(Restrictions.geProperty(propertyName, otherPropertyName));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery ltProperty(@Nonnull String propertyName, @Nonnull String otherPropertyName) {
        criteria.add(Restrictions.ltProperty(propertyName, otherPropertyName));
        return this;
    }

    @Nonnull
    @Override
    public DefaultQuery leProperty(String propertyName, @Nonnull String otherPropertyName) {
        criteria.add(Restrictions.leProperty(propertyName, otherPropertyName));
        return this;
    }

    /**
     * Restricts the results by the given property value range
     *
     * @param property The name of the property
     * @param start The start of the range
     * @param end The end of the range
     * @return This query instance
     */
    @Override
    public DefaultQuery between(String property, QueryParameter start, QueryParameter end) {
        criteria.add(Restrictions.between(property, start, end));
        return this;
    }

    /**
     * Creates a conjunction using two specified criterion
     *
     * @param a The left hand side
     * @param b The right hand side
     * @return This query instance
     */
    public DefaultQuery and(Query.Criterion a, Query.Criterion b) {
        Objects.requireNonNull(a, "Left hand side of AND cannot be null");
        Objects.requireNonNull(b, "Right hand side of AND cannot be null");
        criteria.add(Restrictions.and(a, b));
        return this;
    }

    /**
     * Creates a disjunction using two specified criterion
     *
     * @param a The left hand side
     * @param b The right hand side
     * @return This query instance
     */
    public DefaultQuery or(Query.Criterion a, Query.Criterion b) {
        Objects.requireNonNull(a, "Left hand side of AND cannot be null");
        Objects.requireNonNull(b, "Right hand side of AND cannot be null");
        criteria.add(Restrictions.or(a, b));
        return this;
    }

    private Query.Junction disjunction(Query.Junction currentJunction) {
        Query.Disjunction dis = new Query.Disjunction();
        currentJunction.add(dis);
        return dis;
    }

    private Query.Junction conjunction(Query.Junction currentJunction) {
        Query.Conjunction con = new Query.Conjunction();
        currentJunction.add(con);
        return con;
    }


    /**
     * A criterion is used to restrict the results of a query
     */
    private void addToJunction(Query.Junction currentJunction, Query.Criterion criterion) {
        if (criterion instanceof Query.PropertyCriterion) {
            final Query.PropertyCriterion pc = (Query.PropertyCriterion) criterion;
            Object value = pc.getValue();
            pc.setValue(value);
        }
        if (criterion instanceof Query.Junction) {
            Query.Junction j = (Query.Junction) criterion;
            Query.Junction newj;
            if (j instanceof Query.Disjunction) {
                newj= disjunction(currentJunction);
            } else if (j instanceof Query.Negation) {
                newj= negation(currentJunction);
            }
            else {
                newj= conjunction(currentJunction);
            }
            for (Query.Criterion c : j.getCriteria()) {
                addToJunction(newj, c);
            }
        }
        else {
            currentJunction.add(criterion);
        }
    }


}
