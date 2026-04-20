package io.micronaut.data.tck.entities;

import jakarta.persistence.*;

@Entity
@Access(AccessType.FIELD)
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class AuditedVehicle extends Audited {

    @Id
    @GeneratedValue
    private Long id;

    private String vin;

    public Long getId() {
        return id;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }
}
