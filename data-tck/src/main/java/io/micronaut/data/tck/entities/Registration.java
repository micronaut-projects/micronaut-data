package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedProperty;
import jakarta.persistence.Embedded;

@Embeddable
@Index(columns = "plate_number")
public class Registration {

    private String plateNumber;

    @Index(columns = "status")
    private String status;

    @Embedded
    @MappedProperty("jurisdiction_")
    private Jurisdiction jurisdiction;

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Jurisdiction getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(Jurisdiction jurisdiction) {
        this.jurisdiction = jurisdiction;
    }
}
