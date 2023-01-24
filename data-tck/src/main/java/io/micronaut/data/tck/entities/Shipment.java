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
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name= "Shipment1")
@javax.persistence.Entity
@javax.persistence.Table(name= "Shipment1")
public class Shipment {

    @Creator
    public Shipment(ShipmentId shipmentId, String field) {
        this.shipmentId = shipmentId;
        this.field = field;
    }

    // for hibernate
    public Shipment() {
    }

    @EmbeddedId
    @javax.persistence.EmbeddedId
    private ShipmentId shipmentId;

    @Column(name = "field")
    @javax.persistence.Column(name = "field")
    private String field;

    public ShipmentId getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(ShipmentId shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    @Override
    public String toString() {
        return "Shipment{" +
                "shipmentId=" + shipmentId +
                ", field='" + field + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shipment table = (Shipment) o;
        return Objects.equals(shipmentId, table.shipmentId) &&
                Objects.equals(field, table.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shipmentId, field);
    }
}
