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
package io.micronaut.data.jdbc.mapper.generated;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

/**
 * An entity with embedded values and single ended associations, set through setters.
 */
@MappedEntity("generated_place")
public class Place {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Relation(Relation.Kind.EMBEDDED)
    private Street street;
    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("other_")
    private Street otherStreet;
    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    private GeoPoint point;
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Country country;
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    private GeneratedTypes owner;

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

    public Street getStreet() {
        return street;
    }

    public void setStreet(Street street) {
        this.street = street;
    }

    @Nullable
    public Street getOtherStreet() {
        return otherStreet;
    }

    public void setOtherStreet(@Nullable Street otherStreet) {
        this.otherStreet = otherStreet;
    }

    @Nullable
    public GeoPoint getPoint() {
        return point;
    }

    public void setPoint(@Nullable GeoPoint point) {
        this.point = point;
    }

    @Nullable
    public Country getCountry() {
        return country;
    }

    public void setCountry(@Nullable Country country) {
        this.country = country;
    }

    @Nullable
    public GeneratedTypes getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GeneratedTypes owner) {
        this.owner = owner;
    }
}
