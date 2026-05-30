package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("nitrite_shipment")
public class NitriteShipment {
    @EmbeddedId
    private NitriteShipmentId shipmentId;
    private String field;

    public NitriteShipment() {
    }

    public NitriteShipment(NitriteShipmentId shipmentId, String field) {
        this.shipmentId = shipmentId;
        this.field = field;
    }

    public NitriteShipmentId getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(NitriteShipmentId shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
