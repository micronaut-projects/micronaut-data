/*
 * Copyright 2017-2019 original authors
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
 * Projections used to customize the results of a query.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class Projections {
    public static final QueryModel.IdProjection ID_PROJECTION = new QueryModel.IdProjection();
    public static final QueryModel.CountProjection COUNT_PROJECTION = new QueryModel.CountProjection();

    /**
     * Projection used to obtain the id of an object.
     * @return The IdProjection instance
     */
    public static QueryModel.IdProjection id() {
        return ID_PROJECTION;
    }

    /**
     * Projection that returns the number of records from the query.
     * instead of the results themselves
     *
     * @return The CountProjection instance
     */
    public static QueryModel.CountProjection count() {
        return COUNT_PROJECTION;
    }

    /**
     * A projection that obtains the value of a property of an entity.
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    public static QueryModel.PropertyProjection property(String name) {
        return new QueryModel.PropertyProjection(name);
    }

    /**
     * Computes the sum of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    public static QueryModel.SumProjection sum(String name) {
        return new QueryModel.SumProjection(name);
    }

    /**
     * Computes the min value of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    public static QueryModel.MinProjection min(String name) {
        return new QueryModel.MinProjection(name);
    }

    /**
     * Computes the pageSize value of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    public static QueryModel.MaxProjection max(String name) {
        return new QueryModel.MaxProjection(name);
    }

    /**
     * Computes the average value of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    public static QueryModel.AvgProjection avg(String name) {
        return new QueryModel.AvgProjection(name);
    }

    /**
     * Projection that signifies to return only distinct results.
     *
     * @return Distinct projection
     */
    public static QueryModel.DistinctProjection distinct() {
        return new QueryModel.DistinctProjection();
    }

    /**
     * Projection that signifies to return only distinct results.
     *
     * @param property The name of the property
     * @return Distinct projection
     */
    public static QueryModel.DistinctPropertyProjection distinct(String property) {
        return new QueryModel.DistinctPropertyProjection(property);
    }

    /**
     * Projection that signifies to return only distinct results.
     *
     * @param property The name of the property
     * @return Distinct projection
     */
    public static QueryModel.CountDistinctProjection countDistinct(String property) {
        return new QueryModel.CountDistinctProjection(property);
    }

    /**
     * Defines a group by projection for datastores that support it.
     *
     * @param property The property name
     *
     * @return The projection list
     */
    public static QueryModel.GroupPropertyProjection groupProperty(String property) {
        return new QueryModel.GroupPropertyProjection(property);
    }
}
