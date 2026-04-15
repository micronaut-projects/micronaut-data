package io.micronaut.data.tck.entities;

import jakarta.persistence.Embeddable;

@Embeddable
public class EmployeeId {
    private Long id;
    private String number;

    @SuppressWarnings("checkstyle:DesignForExtension")
    public Long getId() {
        return id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setId(Long id) {
        this.id = id;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public String getNumber() {
        return number;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void setNumber(String number) {
        this.number = number;
    }
}
