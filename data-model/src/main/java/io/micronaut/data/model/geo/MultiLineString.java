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
 * Represents a geometry composed of one or more {@link LineString} values.
 *
 * <p>This type follows the GeoJSON MultiLineString model and exposes helper methods for
 * converting to and from nested coordinate arrays.
 *
 * @param lineStrings the line strings contained in this geometry
 * @since 5.0
 */
@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record MultiLineString(List<LineString> lineStrings) implements Geometry {

    public MultiLineString {
        if (CollectionUtils.isEmpty(lineStrings)) {
            throw new IllegalArgumentException("MultiLineString requires at least one LineString");
        }
        if (lineStrings.contains(null)) {
            throw new IllegalArgumentException("MultiLineString cannot contain null values");
        }
    }

    /**
     * Returns this multi-line string as a list of line coordinate sequences.
     *
     * @return the coordinates of each line string in this geometry
     */
    public List<List<List<Double>>> asCoords() {
        return lineStrings.stream()
            .map(LineString::asCoords)
            .toList();
    }

    /**
     * Creates a {@link MultiLineString} from a list of line coordinate sequences.
     *
     * @param coords the line coordinates to convert
     * @return a multi-line string created from the provided coordinates
     * @throws IllegalArgumentException if {@code coords} is {@code null} or empty
     */
    public static MultiLineString fromCoords(List<List<List<Double>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        if (coords.contains(null)) {
            throw new IllegalArgumentException("Coordinates cannot contain null values");
        }
        return new MultiLineString(coords.stream().map(LineString::fromCoords).toList());
    }
}
