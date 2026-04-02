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
 * Represents a heterogeneous collection of {@link Geometry} values.
 *
 * <p>This type follows the GeoJSON GeometryCollection model and can contain any supported
 * Micronaut Data geometry implementation.
 *
 * @param geometries the geometries contained in this collection
 * @since 5.0
 */
@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record GeometryCollection(List<Geometry> geometries) implements Geometry {

    public GeometryCollection {
        if (CollectionUtils.isEmpty(geometries)) {
            throw new IllegalArgumentException("GeometryCollection requires at least one Geometry");
        }
        if (geometries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("GeometryCollection cannot contain null values");
        }
    }
}
