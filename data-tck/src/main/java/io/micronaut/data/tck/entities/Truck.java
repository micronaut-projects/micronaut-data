package io.micronaut.data.tck.entities;

import jakarta.persistence.Entity;

@Entity
public class Truck extends AuditedVehicle {
    private double maxLoad;

    public double getMaxLoad() {
        return maxLoad;
    }

    public void setMaxLoad(double maxLoad) {
        this.maxLoad = maxLoad;
    }
}
