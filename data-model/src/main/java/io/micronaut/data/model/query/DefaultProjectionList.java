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
import io.micronaut.data.model.query.factory.Projections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of {@link ProjectionList}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
class DefaultProjectionList implements ProjectionList {

    private final List<QueryModel.Projection> projections = new ArrayList(3);

    /**
     * The backing list of projections.
     * @return The list of projections
     */
    public List<QueryModel.Projection> getProjectionList() {
        return Collections.unmodifiableList(projections);
    }

    @Override
    public ProjectionList add(@NonNull QueryModel.Projection p) {
        if (p instanceof QueryModel.CountProjection) {
            if (projections.size() > 1) {
                throw new IllegalArgumentException("Cannot count on more than one projection");
            } else {
                if (projections.isEmpty()) {
                    projections.add(p);
                } else {
                    QueryModel.Projection existing = projections.iterator().next();
                    if (existing instanceof QueryModel.CountProjection) {
                        return this;
                    } else if (existing instanceof QueryModel.PropertyProjection pp) {
                        projections.clear();
                        QueryModel.CountDistinctProjection newProjection = new QueryModel.CountDistinctProjection(pp.getPropertyName());
                        projections.add(newProjection);
                    } else if (existing instanceof QueryModel.IdProjection || existing instanceof QueryModel.DistinctProjection) {
                        projections.clear();
                        projections.add(new QueryModel.CountProjection());
                    }
                }
            }
        } else {
            projections.add(p);
        }
        return this;
    }

    @Override
    public ProjectionList id() {
        add(Projections.id());
        return this;
    }

    @Override
    public ProjectionList count() {
        add(Projections.count());
        return this;
    }

    @Override
    public ProjectionList countDistinct(String property) {
        add(Projections.countDistinct(property));
        return this;
    }

    @Override
    public ProjectionList groupProperty(String property) {
        add(Projections.groupProperty(property));
        return this;
    }

    /**
     * Whether the list is empty.
     * @return True if it is empty
     */
    public boolean isEmpty() {
        return projections.isEmpty();
    }

    @Override
    public ProjectionList distinct() {
        add(Projections.distinct());
        return this;
    }

    @Override
    public ProjectionList distinct(String property) {
        add(Projections.distinct(property));
        return this;
    }

    @Override
    public ProjectionList rowCount() {
        return count();
    }

    /**
     * A projection that obtains the value of a property of an entity.
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    @Override
    public ProjectionList property(String name) {
        add(Projections.property(name));
        return this;
    }

    /**
     * Computes the sum of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    @Override
    public ProjectionList sum(String name) {
        add(Projections.sum(name));
        return this;
    }

    /**
     * Computes the min value of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    @Override
    public ProjectionList min(String name) {
        add(Projections.min(name));
        return this;
    }

    /**
     * Computes the pageSize value of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    @Override
    public ProjectionList max(String name) {
        add(Projections.max(name));
        return this;
    }

    /**
     * Computes the average value of a property.
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    @Override
    public ProjectionList avg(String name) {
        add(Projections.avg(name));
        return this;
    }
}
