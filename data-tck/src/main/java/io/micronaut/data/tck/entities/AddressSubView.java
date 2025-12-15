package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;

@Embeddable
@JsonSubView(entity = Address.class)
public class AddressSubView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private final String street;
    private final String zipCode;

    public AddressSubView(String street, String zipCode) {
        this.street = street;
        this.zipCode = zipCode;
    }

    public String getStreet() {
        return street;
    }

    public String getZipCode() {
        return zipCode;
    }

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public static AddressSubView fromAddress(Address address) {
        AddressSubView addressSubView = new AddressSubView(address.getStreet(), address.getZipCode());
        addressSubView.setId(address.getId());
        return addressSubView;
    }
}
