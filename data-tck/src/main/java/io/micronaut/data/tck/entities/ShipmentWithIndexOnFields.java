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
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import java.util.Objects;

@MappedEntity
public class ShipmentWithIndexOnFields {

    @Creator
    public ShipmentWithIndexOnFields(Long shipmentId, String field, String taxCode) {
        this.shipmentId = shipmentId;
        this.field = field;
        this.taxCode = taxCode;
    }

    // for hibernate
    public ShipmentWithIndexOnFields() {
    }

    @Id
    @GeneratedValue
    private Long shipmentId;

    @Column(name = "field")
    @Index(columns = "field", unique = true)
    private String field;


    @Column(name = "taxCode")
    @Index(columns = "taxCode", unique = false)
    private String taxCode;

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
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
        ShipmentWithIndexOnFields table = (ShipmentWithIndexOnFields) o;
        return Objects.equals(shipmentId, table.shipmentId) &&
                Objects.equals(field, table.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shipmentId, field);
    }
}
