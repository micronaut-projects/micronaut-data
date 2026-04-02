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
 * Represents a geometry composed of one or more {@link Point} values.
 *
 * <p>This type follows the GeoJSON MultiPoint model and provides helpers for converting
 * between point instances and their coordinate representation.
 *
 * @param points the points contained in this geometry
 * @since 5.0
 */
@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record MultiPoint(List<Point> points) implements Geometry {

    public MultiPoint {
        if (CollectionUtils.isEmpty(points)) {
            throw new IllegalArgumentException("MultiPoint requires at least one Point");
        }
        if (points.contains(null)) {
            throw new IllegalArgumentException("MultiPoint cannot contain null values");
        }
    }

    /**
     * Returns this multi-point geometry as a list of point coordinate pairs.
     *
     * @return the coordinates of each point in this geometry
     */
    public List<List<Double>> asCoords() {
        return points.stream()
            .map(Point::asCoords)
            .toList();
    }

    /**
     * Creates a {@link MultiPoint} from a list of point coordinate pairs.
     *
     * @param coords the point coordinates to convert
     * @return a multi-point geometry created from the provided coordinates
     * @throws IllegalArgumentException if {@code coords} is {@code null} or empty
     */
    public static MultiPoint fromCoords(List<List<Double>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        if (coords.contains(null)) {
            throw new IllegalArgumentException("Coordinates cannot contain null values");
        }
        return new MultiPoint(coords.stream().map(Point::fromCoords).toList());
    }
}
