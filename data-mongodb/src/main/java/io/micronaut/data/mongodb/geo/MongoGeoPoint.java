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
package io.micronaut.data.mongodb.geo;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Minimal GeoJSON point-like value for MongoDB geospatial fields.
 *
 * @param x longitude / x coordinate
 * @param y latitude / y coordinate
 * @author radovanradic
 * @since 5.0.0
 */
@Serdeable
public record MongoGeoPoint(double x, double y) implements MongoGeoPointLike, MongoGeoGeometry {
}
