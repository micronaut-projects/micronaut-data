package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class NitriteMpAddress {
    private String street;
    private String zipCode;

    public NitriteMpAddress() {
    }

    public NitriteMpAddress(String street, String zipCode) {
        this.street = street;
        this.zipCode = zipCode;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }
}
