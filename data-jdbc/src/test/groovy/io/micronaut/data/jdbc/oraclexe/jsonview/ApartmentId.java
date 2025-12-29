package io.micronaut.data.jdbc.oraclexe.jsonview;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class ApartmentId implements Serializable {
    private int buildingId;
    private int flatId;

    public ApartmentId(int buildingId, int flatId) {
        this.buildingId = buildingId;
        this.flatId = flatId;
    }

    public ApartmentId() {

    }

    public int getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(int buildingId) {
        this.buildingId = buildingId;
    }

    public int getFlatId() {
        return flatId;
    }

    public void setFlatId(int flatId) {
        this.flatId = flatId;
    }
}
