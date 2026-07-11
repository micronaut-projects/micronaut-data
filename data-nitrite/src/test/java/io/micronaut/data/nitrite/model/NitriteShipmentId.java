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
package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class NitriteShipmentId implements Serializable {
    private String warehouseId;
    private String shipmentNumber;

    public NitriteShipmentId() {
    }

    public NitriteShipmentId(String warehouseId, String shipmentNumber) {
        this.warehouseId = warehouseId;
        this.shipmentNumber = shipmentNumber;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public void setShipmentNumber(String shipmentNumber) {
        this.shipmentNumber = shipmentNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NitriteShipmentId that = (NitriteShipmentId) o;
        return Objects.equals(warehouseId, that.warehouseId) &&
               Objects.equals(shipmentNumber, that.shipmentNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, shipmentNumber);
    }
}
