package io.micronaut.data.nitrite.mongoport.entities;

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
