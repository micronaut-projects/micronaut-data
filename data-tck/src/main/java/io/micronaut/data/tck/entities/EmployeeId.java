package io.micronaut.data.tck.entities;

import jakarta.persistence.Embeddable;

@Embeddable
@SuppressWarnings("checkstyle:DesignForExtension")
public class EmployeeId {
    private Long id;
    private String number;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
