package io.micronaut.data.tck.entities;

import jakarta.persistence.Entity;

@Entity
public class Bus extends AuditedVehicle {
    private int seatCount;

    public int getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(int seatCount) {
        this.seatCount = seatCount;
    }
}
