package io.micronaut.data.model.query;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Models a list of projections
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ProjectionList {
    /**
     * A Projection that obtains the id of an object
     * @return The projection list
     */
    ProjectionList id();

    /**
     * Count the number of records returned
     * @return The projection list
     */
    ProjectionList count();

    /**
     * Count the number of records returned
     * @param property The property name to count
     * @return The projection list
     */
    ProjectionList countDistinct(String property);

    /**
     * Defines a group by projection for datastores that support it
     *
     * @param property The property name
     *
     * @return The projection list
     */
    ProjectionList groupProperty(String property);

    /**
     * Projection to return only distinct records
     *
     * @return The projection list
     */
    ProjectionList distinct();

    /**
     * Projection to return only distinct properties
     *
     * @param property The property name to use
     *
     * @return The projection list
     */
    ProjectionList distinct(String property);

    /**
     * Count the number of records returned
     * @return The projection list
     */
    ProjectionList rowCount();

    /**
     * A projection that obtains the value of a property of an entity
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList property(String name);

    /**
     * Computes the sum of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList sum(String name);

    /**
     * Computes the min value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList min(String name);

    /**
     * Computes the max value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList max(String name);

    /**
     * Computes the average value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList avg(String name);

    /**
     * Adds a projection to the projection list.
     * @param projection The projection to add
     * @return This list
     */
    ProjectionList add(@NonNull Query.Projection projection);
}
