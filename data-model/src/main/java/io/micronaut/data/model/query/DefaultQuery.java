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
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.factory.Restrictions;
import java.util.*;

/**
 * Models a query that can be executed against a data store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultQuery implements QueryModel {

    private final PersistentEntity entity;

    private QueryModel.Junction criteria = new QueryModel.Conjunction();
    private DefaultProjectionList projections = new DefaultProjectionList();
    private int max = -1;
    private long offset = 0;
    private Map<String, JoinPath> joinPaths = new HashMap<>(2);
    private Sort sort = Sort.unsorted();
    private boolean forUpdate;

    /**
     * Default constructor.
     * @param entity The entity the query applies to.
     */
    protected DefaultQuery(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        this.entity = entity;
    }

    /**
     * @return The join paths.
     */
    @Override
    public Collection<JoinPath> getJoinPaths() {
        return Collections.unmodifiableCollection(joinPaths.values());
    }

    /**
     * Creates an association query.
     *
     * @param associationName The assocation name
     * @return The Query instance
     */
    public AssociationQuery createQuery(String associationName) {
        final PersistentProperty property = entity.getPropertyByPath(associationName).orElse(null);
        if (!(property instanceof Association)) {
            throw new IllegalArgumentException("Cannot query association [" +
                    associationName + "] of class [" + entity +
                    "]. The specified property is not an association.");
        }

        Association association = (Association) property;
        return new AssociationQuery(associationName, association);
    }

    @NonNull
    @Override
    public PersistentEntity getPersistentEntity() {
        return entity;
    }

    /**
     * @return The criteria defined by this query
     */
    public QueryModel.Junction getCriteria() {
        return criteria;
    }

    @NonNull
    @Override
    public List<Projection> getProjections() {
        return projections.getProjectionList();
    }


    /**
     * Obtain the joint for for a given association.
     * @param path The path to the association
     * @return The join type
     */
    @Override
    public Optional<JoinPath> getJoinPath(String path) {
        if (path != null) {
            return Optional.ofNullable(joinPaths.get(path));
        }
        return Optional.empty();
    }

    /**
     * Specifies whether a join query should be used (if join queries are supported by the underlying datastore).
     *
     * @param path The path
     * @param joinType The joinType
     * @param alias The alias
     * @return The query
     */
    @Override
    public JoinPath join(@NonNull String path, @NonNull Join.Type joinType, String alias) {
        PersistentEntity entity = getEntity();
        List<PersistentProperty> propertiesInPath = entity.getPropertiesInPath(path);
        if (propertiesInPath.isEmpty()) {
            throw new IllegalArgumentException("Invalid association path. Element [" + path + "] is not an association.");
        }
        List<Association> associations = new ArrayList<>(propertiesInPath.size());
        for (PersistentProperty property : propertiesInPath) {
            if (property instanceof Association) {
                associations.add((Association) property);
            } else {
                throw new IllegalArgumentException("Invalid association path. Property [" + property + "] is not an association.");
            }
        }
        Association[] associationPath = associations.toArray(new Association[0]);
        JoinPath jp = new JoinPath(path, associationPath, joinType, alias);
        joinPaths.put(path, jp);
        return jp;
    }

    @NonNull
    @Override
    public JoinPath join(String path, Association association, @NonNull Join.Type joinType, @Nullable String alias) {
        return join(path, joinType, alias);
    }

    /**
     * @return The projections for this query.
     */
    @Override
    public ProjectionList projections() {
        return projections;
    }

    /**
     * Adds the specified criterion instance to the query.
     *
     * @param criterion The criterion instance
     */
    @Override
    public @NonNull
    QueryModel add(@NonNull QueryModel.Criterion criterion) {
        ArgumentUtils.requireNonNull("criterion", criterion);
        QueryModel.Junction currentJunction = criteria;
        add(currentJunction, criterion);
        return this;
    }

    /**
     * Adds the specified criterion instance to the given junction.
     *
     * @param currentJunction The junction to add the criterion to
     * @param criterion The criterion instance
     */
    private void add(QueryModel.Junction currentJunction, QueryModel.Criterion criterion) {
        addToJunction(currentJunction, criterion);
    }

    /**
     * @return The PersistentEntity being query
     */
    public PersistentEntity getEntity() {
        return entity;
    }

    /**
     * Creates a disjunction (OR) query.
     * @return The Junction instance
     */
    public QueryModel.Junction disjunction() {
        QueryModel.Junction currentJunction = criteria;
        return disjunction(currentJunction);
    }

    /**
     * Creates a conjunction (AND) query.
     * @return The Junction instance
     */
    public QueryModel.Junction conjunction() {
        QueryModel.Junction currentJunction = criteria;
        return conjunction(currentJunction);
    }

    /**
     * Creates a negation of several criterion.
     * @return The negation
     */
    public QueryModel.Junction negation() {
        QueryModel.Junction currentJunction = criteria;
        return negation(currentJunction);
    }

    private QueryModel.Junction negation(QueryModel.Junction currentJunction) {
        QueryModel.Negation dis = new QueryModel.Negation();
        currentJunction.add(dis);
        return dis;
    }

    /**
     * Defines the maximum number of results to return.
     * @param max The pageSize results
     * @return This query instance
     */
    @Override
    public DefaultQuery max(int max) {
        this.max = max;
        return this;
    }

    @Override
    public int getMax() {
        return this.max;
    }

    @Override
    public long getOffset() {
        return this.offset;
    }

    @Override
    public void forUpdate() {
        forUpdate = true;
    }

    @Override
    public boolean isForUpdate() {
        return forUpdate;
    }

    /**
     * Defines the offset (the first result index) of the query.
     * @param offset The offset
     * @return This query instance
     */
    @Override
    public DefaultQuery offset(long offset) {
        this.offset = offset;
        return this;
    }

    @Override
    public Sort getSort() {
        return this.sort;
    }

    @NonNull
    @Override
    public QueryModel sort(@NonNull Sort sort) {
        ArgumentUtils.requireNonNull("sort", sort);
        this.sort = sort;
        return this;
    }

    /**
     * Restricts the results by the given properties value.
     *
     * @param property The name of the property
     * @param parameter The parameter that provides the value
     * @return This query instance
     */
    @Override
    public @NonNull
    DefaultQuery eq(@NonNull String property, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.eq(property, parameter));
        return this;
    }

    /**
     * Shortcut to restrict the query to multiple given property values.
     *
     * @param values The values
     * @return This query instance
     */
    public @NonNull
    DefaultQuery allEq(@NonNull Map<String, QueryParameter> values) {
        QueryModel.Junction conjunction = conjunction();
        for (String property : values.keySet()) {
            QueryParameter value = values.get(property);
            conjunction.add(Restrictions.eq(property, value));
        }
        return this;
    }

    @NonNull
    @Override
    public QueryModel eqAll(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel gtAll(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel ltAll(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel geAll(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel leAll(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel gtSome(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel geSome(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel ltSome(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel leSome(@NonNull String propertyName, @NonNull Criteria propertyValue) {
        return null;
    }

    @NonNull
    @Override
    public QueryModel idEquals(QueryParameter parameter) {
        return null;
    }

    /**
     * Used to restrict a value to be empty (such as a blank string or an empty collection).
     *
     * @param property The property name
     */
    @Override
    public @NonNull
    DefaultQuery isEmpty(@NonNull String property) {
        criteria.add(Restrictions.isEmpty(property));
        return this;
    }

    /**
     * Used to restrict a value to be not empty (such as a blank string or an empty collection).
     *
     * @param property The property name
     */
    @Override
    public @NonNull
    DefaultQuery isNotEmpty(@NonNull String property) {
        criteria.add(Restrictions.isNotEmpty(property));
        return this;
    }

    /**
     * Used to restrict a property to be null.
     *
     * @param property The property name
     */
    @Override
    public @NonNull
    DefaultQuery isNull(@NonNull String property) {
        criteria.add(Restrictions.isNull(property));
        return this;
    }

    @NonNull
    @Override
    public QueryModel isTrue(@NonNull String propertyName) {
        criteria.add(Restrictions.isTrue(propertyName));
        return this;
    }

    @NonNull
    @Override
    public QueryModel isFalse(@NonNull String propertyName) {
        criteria.add(Restrictions.isFalse(propertyName));
        return this;
    }

    /**
     * Used to restrict a property to be not null.
     *
     * @param property The property name
     */
    @Override
    public @NonNull
    DefaultQuery isNotNull(@NonNull String property) {
        criteria.add(Restrictions.isNotNull(property));
        return this;
    }

    /**
     * Restricts the results by the given properties value.
     *
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public @NonNull
    DefaultQuery idEq(@NonNull QueryParameter value) {
        criteria.add(Restrictions.idEq(value));
        return this;
    }

    @NonNull
    @Override
    public QueryModel ne(@NonNull String propertyName, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.ne(propertyName, parameter));
        return this;
    }

    /**
     * Used to restrict a value to be greater than the given value.
     *
     * @param property The name of the property
     * @param value The value to restrict by
     * @return This query instance
     */
    @Override
    public @NonNull
    DefaultQuery gt(@NonNull String property, @NonNull QueryParameter value) {
        criteria.add(Restrictions.gt(property, value));
        return this;
    }

    /**
     * Used to restrict a value to be greater than or equal to the given value.
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
     * Used to restrict a value to be less than or equal to the given value.
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
     * Used to restrict a value to be greater than or equal to the given value.
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
     * Used to restrict a value to be less than or equal to the given value.
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
     * Used to restrict a value to be less than the given value.
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

    @NonNull
    @Override
    public DefaultQuery like(@NonNull String propertyName, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.like(propertyName, parameter));
        return this;
    }

    @NonNull
    @Override
    public QueryModel startsWith(@NonNull String propertyName, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.startsWith(propertyName, parameter));
        return this;
    }

    @NonNull
    @Override
    public QueryModel endsWith(@NonNull String propertyName, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.endsWith(propertyName, parameter));
        return this;
    }

    @NonNull
    @Override
    public QueryModel contains(@NonNull String propertyName, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.contains(propertyName, parameter));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery ilike(@NonNull String propertyName, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.ilike(propertyName, parameter));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery rlike(@NonNull String propertyName, @NonNull QueryParameter parameter) {
        criteria.add(Restrictions.rlike(propertyName, parameter));
        return this;
    }

    @NonNull
    @Override
    public QueryModel and(@NonNull Criteria other) {
        // TODO
        return this;
    }

    @NonNull
    @Override
    public QueryModel or(@NonNull Criteria other) {
        // TODO
        return this;
    }

    @NonNull
    @Override
    public QueryModel not(@NonNull Criteria other) {
        // TODO
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery inList(@NonNull String propertyName, @NonNull QueryModel subquery) {
        criteria.add(Restrictions.in(propertyName, subquery));
        return this;
    }

    /**
     * Restricts the results by the given property values.
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

    @NonNull
    @Override
    public DefaultQuery notIn(@NonNull String propertyName, @NonNull QueryModel subquery) {
        criteria.add(Restrictions.notIn(propertyName, subquery));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery sizeEq(@NonNull String propertyName, @NonNull QueryParameter size) {
        criteria.add(Restrictions.sizeEq(propertyName, size));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery sizeGt(@NonNull String propertyName, @NonNull QueryParameter size) {
        criteria.add(Restrictions.sizeGt(propertyName, size));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery sizeGe(@NonNull String propertyName, @NonNull QueryParameter size) {
        criteria.add(Restrictions.sizeGe(propertyName, size));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery sizeLe(@NonNull String propertyName, @NonNull QueryParameter size) {
        criteria.add(Restrictions.sizeLe(propertyName, size));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery sizeLt(@NonNull String propertyName, @NonNull QueryParameter size) {
        criteria.add(Restrictions.sizeLt(propertyName, size));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery sizeNe(@NonNull String propertyName, @NonNull QueryParameter size) {
        criteria.add(Restrictions.sizeNe(propertyName, size));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery eqProperty(@NonNull String propertyName, @NonNull String otherPropertyName) {
        criteria.add(Restrictions.eqProperty(propertyName, otherPropertyName));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery neProperty(@NonNull String propertyName, @NonNull String otherPropertyName) {
        criteria.add(Restrictions.neProperty(propertyName, otherPropertyName));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery gtProperty(@NonNull String propertyName, @NonNull String otherPropertyName) {
        criteria.add(Restrictions.gtProperty(propertyName, otherPropertyName));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery geProperty(@NonNull String propertyName, @NonNull String otherPropertyName) {
        criteria.add(Restrictions.geProperty(propertyName, otherPropertyName));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery ltProperty(@NonNull String propertyName, @NonNull String otherPropertyName) {
        criteria.add(Restrictions.ltProperty(propertyName, otherPropertyName));
        return this;
    }

    @NonNull
    @Override
    public DefaultQuery leProperty(String propertyName, @NonNull String otherPropertyName) {
        criteria.add(Restrictions.leProperty(propertyName, otherPropertyName));
        return this;
    }

    /**
     * Restricts the results by the given property value range.
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
     * Creates a conjunction using two specified criterion.
     *
     * @param a The left hand side
     * @param b The right hand side
     * @return This query instance
     */
    public DefaultQuery and(QueryModel.Criterion a, QueryModel.Criterion b) {
        Objects.requireNonNull(a, "Left hand side of AND cannot be null");
        Objects.requireNonNull(b, "Right hand side of AND cannot be null");
        criteria.add(Restrictions.and(a, b));
        return this;
    }

    /**
     * Creates a disjunction using two specified criterion.
     *
     * @param a The left hand side
     * @param b The right hand side
     * @return This query instance
     */
    public DefaultQuery or(QueryModel.Criterion a, QueryModel.Criterion b) {
        Objects.requireNonNull(a, "Left hand side of AND cannot be null");
        Objects.requireNonNull(b, "Right hand side of AND cannot be null");
        criteria.add(Restrictions.or(a, b));
        return this;
    }

    private QueryModel.Junction disjunction(QueryModel.Junction currentJunction) {
        QueryModel.Disjunction dis = new QueryModel.Disjunction();
        currentJunction.add(dis);
        return dis;
    }

    private QueryModel.Junction conjunction(QueryModel.Junction currentJunction) {
        QueryModel.Conjunction con = new QueryModel.Conjunction();
        currentJunction.add(con);
        return con;
    }

    private void addToJunction(QueryModel.Junction currentJunction, QueryModel.Criterion criterion) {
        if (criterion instanceof QueryModel.PropertyCriterion) {
            final QueryModel.PropertyCriterion pc = (QueryModel.PropertyCriterion) criterion;
            Object value = pc.getValue();
            pc.setValue(value);
        }
        if (criterion instanceof QueryModel.Junction) {
            QueryModel.Junction j = (QueryModel.Junction) criterion;
            QueryModel.Junction newj;
            if (j instanceof QueryModel.Disjunction) {
                newj = disjunction(currentJunction);
            } else if (j instanceof QueryModel.Negation) {
                newj = negation(currentJunction);
            } else {
                newj = conjunction(currentJunction);
            }
            for (QueryModel.Criterion c : j.getCriteria()) {
                addToJunction(newj, c);
            }
        } else {
            currentJunction.add(criterion);
        }
    }

}
