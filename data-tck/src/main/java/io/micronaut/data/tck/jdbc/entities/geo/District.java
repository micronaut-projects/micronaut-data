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
package io.micronaut.data.tck.jdbc.entities.geo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.geo.Polygon;
import jakarta.persistence.GeneratedValue;

import java.util.List;

import static io.micronaut.data.annotation.Relation.Kind.ONE_TO_MANY;

@MappedEntity
public class District {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private Polygon area;

    @Relation(value = ONE_TO_MANY, mappedBy = "district")
    private List<School> schools;

    @Relation(value = ONE_TO_MANY, mappedBy = "district")
    private List<HotelWkt> hotelsWkt;

    @Relation(value = ONE_TO_MANY, mappedBy = "district")
    private List<HotelJson> hotelsJson;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @MappedProperty(value = "geometryEntityJson")
    private GeometryEntityJson geometryEntityJson;

    @Relation(Relation.Kind.MANY_TO_ONE)
    @MappedProperty(value = "geometryEntityWkt")
    private GeometryEntityWkt geometryEntityWkt;

    public District(Long id, String name, Polygon area, GeometryEntityJson geometryEntityJson, GeometryEntityWkt geometryEntityWkt) {
        this.id = id;
        this.name = name;
        this.area = area;
        this.geometryEntityJson = geometryEntityJson;
        this.geometryEntityWkt = geometryEntityWkt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Polygon getArea() {
        return area;
    }

    public void setArea(Polygon area) {
        this.area = area;
    }

    public List<School> getSchools() {
        return schools;
    }

    public void setSchools(List<School> schools) {
        this.schools = schools;
    }

    public List<HotelWkt> getHotelsWkt() {
        return hotelsWkt;
    }

    public void setHotelsWkt(List<HotelWkt> hotelsWkt) {
        this.hotelsWkt = hotelsWkt;
    }

    public List<HotelJson> getHotelsJson() {
        return hotelsJson;
    }

    public void setHotelsJson(List<HotelJson> hotelsJson) {
        this.hotelsJson = hotelsJson;
    }


    public GeometryEntityJson getGeometryEntityJson() {
        return geometryEntityJson;
    }

    public void setGeometryEntityJson(GeometryEntityJson geometryEntityJson) {
        this.geometryEntityJson = geometryEntityJson;
    }

    public GeometryEntityWkt getGeometryEntityWkt() {
        return geometryEntityWkt;
    }

    public void setGeometryEntityWkt(GeometryEntityWkt geometryEntityWkt) {
        this.geometryEntityWkt = geometryEntityWkt;
    }
}
