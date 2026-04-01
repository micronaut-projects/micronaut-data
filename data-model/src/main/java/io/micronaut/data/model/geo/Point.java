/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.GeometryJsonConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * A record that represents a two-dimensional point with {@code x} and {@code y} coordinates.
 *
 * <p>This record implements the {@link Geometry} interface and can be used in geographic
 * data models compliant with the GeoJSON specification. The coordinates are interpreted
 * as (longitude, latitude) or (x, y) depending on context.
 *
 * @param x the x-coordinate (typically longitude)
 * @param y the y-coordinate (typically latitude)
 * @since 5.0
 */
@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record Point(double x, double y) implements Geometry {

    /**
     * Returns the coordinates of this point as a list of two {@code Double} values.
     *
     * @return a list containing the x and y coordinates, in that order
     */
    public List<Double> asCoords() {
        return List.of(x, y);
    }

    /**
     * Creates a {@link Point} instance from a list of coordinates.
     *
     * @param coords a list containing exactly two {@code Double} elements representing x and y
     * @return a new {@link Point} created from the given coordinates
     * @throws IllegalArgumentException if {@code coords} is {@code null}, empty, or does not contain exactly two elements
     */
    public static Point fromCoords(List<Double> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        if (coords.size() != 2) {
            throw new IllegalArgumentException("Coordinates must have 2 elements");
        }
        if (coords.contains(null)) {
            throw new IllegalArgumentException("Coordinates cannot contain null values");
        }
        return new Point(coords.get(0), coords.get(1));
    }
}
