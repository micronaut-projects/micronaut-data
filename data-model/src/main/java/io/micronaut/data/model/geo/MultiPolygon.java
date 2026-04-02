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
 * Represents a geometry composed of one or more {@link Polygon} values.
 *
 * <p>This type follows the GeoJSON MultiPolygon model and can be converted to nested
 * polygon coordinate structures for serialization.
 *
 * @param polygons the polygons contained in this geometry
 * @since 5.0
 */
@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record MultiPolygon(List<Polygon> polygons) implements Geometry {

    public MultiPolygon {
        if (CollectionUtils.isEmpty(polygons)) {
            throw new IllegalArgumentException("MultiPolygon requires at least one Polygon");
        }
        if (polygons.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("MultiPolygon cannot contain null Polygons");
        }
    }

    /**
     * Returns this multi-polygon as a list of polygon ring coordinate sequences.
     *
     * @return the coordinates of each polygon in this geometry
     */
    public List<List<List<List<Double>>>> asCoords() {
        return polygons.stream()
            .map(Polygon::asCoords)
            .toList();
    }

    /**
     * Creates a {@link MultiPolygon} from a list of polygon ring coordinate sequences.
     *
     * @param coords the polygon coordinates to convert
     * @return a multi-polygon created from the provided coordinates
     * @throws IllegalArgumentException if {@code coords} is {@code null} or empty
     */
    public static MultiPolygon fromCoords(List<List<List<List<Double>>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("List of Polygon coordinates cannot be null nor empty");
        }
        return new MultiPolygon(coords.stream().map(Polygon::fromCoords).toList());
    }
}
