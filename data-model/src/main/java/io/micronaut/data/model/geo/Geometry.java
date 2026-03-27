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

/**
 * Base sealed interface for geometry values supported by Micronaut Data geo types.
 *
 * <p>The permitted implementations mirror the core GeoJSON geometry model and are used by
 * the built-in geometry converters for WKT and GeoJSON serialization.
 *
 * @since 5.0
 */
public sealed interface Geometry permits Point, MultiPoint, LineString,
    MultiLineString, Polygon, MultiPolygon, GeometryCollection {
}
