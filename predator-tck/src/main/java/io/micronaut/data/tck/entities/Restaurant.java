package io.micronaut.data.tck.entities;

import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Restaurant {

    @GeneratedValue
    @Id
    private Long id;
    private final String name;

    @Embedded
    private final Address address;


    public Restaurant(String name, Address address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public Address getAddress() {
        return address;
    }
}
