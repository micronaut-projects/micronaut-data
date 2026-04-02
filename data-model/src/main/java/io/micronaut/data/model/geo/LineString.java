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
import java.util.Objects;

/**
 * Represents a line geometry defined by an ordered list of at least two {@link Point} values.
 *
 * <p>This type follows the GeoJSON LineString model and can be converted to nested coordinate
 * lists for serialization.
 *
 * @param points the ordered points that define the line string
 * @since 5.0
 */
@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record LineString(List<Point> points) implements Geometry {

    public LineString {
        if (CollectionUtils.isEmpty(points) || points.size() < 2) {
            throw new IllegalArgumentException("LineString requires at least two Points");
        }
        if (points.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("LineString cannot contain null values");
        }
    }

    /**
     * Returns this line string as an ordered list of point coordinate pairs.
     *
     * @return the coordinates of each point in this line string
     */
    public List<List<Double>> asCoords() {
        return points.stream()
            .map(Point::asCoords)
            .toList();
    }

    /**
     * Creates a {@link LineString} from an ordered list of point coordinate pairs.
     *
     * @param coords the point coordinates to convert
     * @return a line string created from the provided coordinates
     * @throws IllegalArgumentException if {@code coords} is {@code null}, empty, or contains fewer than two points
     */
    public static LineString fromCoords(List<List<Double>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        if (coords.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Coordinates cannot contain null values");
        }
        return new LineString(coords.stream().map(Point::fromCoords).toList());
    }
}
