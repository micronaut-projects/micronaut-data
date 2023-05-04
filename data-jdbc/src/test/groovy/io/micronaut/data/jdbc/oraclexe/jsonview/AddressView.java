package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;

@Embeddable
public class AddressView {

    @Id
    private Long addressID;
    private String street;

    private String city;

    public Long getAddressID() {
        return addressID;
    }

    public void setAddressID(Long addressID) {
        this.addressID = addressID;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public static AddressView fromAddress(Address address) {
        AddressView addressView = new AddressView();
        addressView.setAddressID(address.getId());
        addressView.setCity(addressView.getCity());
        addressView.setStreet(addressView.getStreet());
        return addressView;
    }
}
