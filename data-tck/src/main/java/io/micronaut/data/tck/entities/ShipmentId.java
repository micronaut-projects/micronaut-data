/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Creator;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@javax.persistence.Embeddable
public class ShipmentId implements Serializable {

    @Column(name= "sp_country")
    @javax.persistence.Column(name= "sp_country")
    private String country;

    @Column(name= "sp_city")
    @javax.persistence.Column(name= "sp_city")
    private String city;

    @Creator
    public ShipmentId(String country, String city) {
        this.country = country;
        this.city = city;
    }

    public ShipmentId() {
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setCity(String city) {
        this.city = city;
    }

    @Override
    public String toString() {
        return "TableId{" +
                "p='" + country + '\'' +
                ", t='" + city + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShipmentId tableId = (ShipmentId) o;
        return Objects.equals(country, tableId.country) &&
                Objects.equals(city, tableId.city);
    }

    @Override
    public int hashCode() {
        return Objects.hash(country, city);
    }
}
