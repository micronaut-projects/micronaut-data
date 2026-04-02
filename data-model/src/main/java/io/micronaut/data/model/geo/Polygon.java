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
 * Represents a polygon defined by one or more closed {@link LineString} rings.
 *
 * <p>The first ring describes the outer boundary and any subsequent rings describe holes,
 * matching the GeoJSON Polygon structure.
 *
 * @param lineStrings the closed rings that make up the polygon
 * @since 5.0
 */
@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record Polygon(List<LineString> lineStrings) implements Geometry {

    public Polygon {
        if (CollectionUtils.isEmpty(lineStrings)) {
            throw new IllegalArgumentException("Polygon requires at least one ring (outer boundary)");
        }
        if (lineStrings.contains(null)) {
            throw new IllegalArgumentException("Polygon cannot contain null values");
        }
        for (int i = 0; i < lineStrings.size(); i++) {
            LineString ring = lineStrings.get(i);
            if (ring.points().size() < 4) {
                throw new IllegalArgumentException(String.format("Ring at index %d must have at least 4 points (got %d)", i, ring.points().size()));
            }
            Point first = ring.points().getFirst();
            Point last = ring.points().getLast();
            if (!first.equals(last)) {
                throw new IllegalArgumentException(String.format("Ring at index %d is not closed: the first point is not equal to the last point", i));
            }
        }
    }

    /**
     * Returns this polygon as a list of ring coordinate sequences.
     *
     * @return the coordinates of each ring in this polygon
     */
    public List<List<List<Double>>> asCoords() {
        return lineStrings.stream()
            .map(LineString::asCoords)
            .toList();
    }

    /**
     * Creates a {@link Polygon} from a list of ring coordinate sequences.
     *
     * @param coords the polygon ring coordinates to convert
     * @return a polygon created from the provided coordinates
     * @throws IllegalArgumentException if {@code coords} is {@code null}, empty, or describes an invalid polygon ring
     */
    public static Polygon fromCoords(List<List<List<Double>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        if (coords.contains(null)) {
            throw new IllegalArgumentException("Coordinates cannot contain null values");
        }
        return new Polygon(coords.stream().map(LineString::fromCoords).toList());
    }
}
