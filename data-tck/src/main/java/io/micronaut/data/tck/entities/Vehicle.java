package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import jakarta.persistence.Embedded;

@MappedEntity
public class Vehicle {

    @GeneratedValue
    @Id
    private Long id;

    @Index(columns = "name")
    private String name;

    @Embedded
    private Registration firstRegistration;

    @Embedded
    @MappedProperty("second_")
    private Registration secondRegistration;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Registration getFirstRegistration() {
        return firstRegistration;
    }

    public void setFirstRegistration(Registration firstRegistration) {
        this.firstRegistration = firstRegistration;
    }

    public Registration getSecondRegistration() {
        return secondRegistration;
    }

    public void setSecondRegistration(Registration secondRegistration) {
        this.secondRegistration = secondRegistration;
    }
}
