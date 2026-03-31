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
package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.model.geo.MultiPoint;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.runtime.convert.GeometryWktConverter;

//tag::get[]
@MappedEntity
public class GeographyEntityWkt {
    //end::get[]

    @Id
    @GeneratedValue
    private Long id;
    //tag::get[]

    @Srid(4258)
    @Index(columns = "location")
    @MappedProperty(value = "location", converter = GeometryWktConverter.class, definition = "geography not null")
    private Point point;
    //end::get[]

    @Nullable
    private MultiPoint multiPoint;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }

    public MultiPoint getMultiPoint() {
        return multiPoint;
    }

    public void setMultiPoint(MultiPoint multiPoint) {
        this.multiPoint = multiPoint;
    }
    //tag::get[]
}
//end::get[]
