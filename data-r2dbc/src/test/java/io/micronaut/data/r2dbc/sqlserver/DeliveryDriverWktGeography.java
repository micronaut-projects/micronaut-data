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
package io.micronaut.data.r2dbc.sqlserver;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.runtime.convert.GeometryWktConverter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@MappedEntity
public record DeliveryDriverWktGeography(
    @Id
    @GeneratedValue
    Long id,

    @NotBlank
    String name,

    @NotNull
    Status status,

    @NotNull
    @Srid(value = 4326, type = Srid.CrsType.GEOGRAPHIC)
    @Index(columns = "location")
    @MappedProperty(converter = GeometryWktConverter.class, definition = "geography not null")
    Point location
) {

    public enum Status {
        AVAILABLE,
        BUSY
    }

    public DeliveryDriverWktGeography(String name, Status status, Point location) {
        this(null, name, status, location);
    }
}
