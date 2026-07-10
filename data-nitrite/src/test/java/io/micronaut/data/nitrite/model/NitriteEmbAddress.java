package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class NitriteEmbAddress {
    private String street;
    private String zipCode;

    public NitriteEmbAddress() {
    }

    public NitriteEmbAddress(String street, String zipCode) {
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
