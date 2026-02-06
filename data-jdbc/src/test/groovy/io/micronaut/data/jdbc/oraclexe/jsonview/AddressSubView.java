package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Id;

@Embeddable
@JsonSubView(entity = Address.class, operations = { JsonView.Operation.UPDATE, JsonView.Operation.INSERT })
public class AddressSubView {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @MappedProperty("id")
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

    public static AddressSubView fromAddress(Address address) {
        AddressSubView addressSubView = new AddressSubView();
        addressSubView.setAddressID(address.getId());
        addressSubView.setCity(address.getCity());
        addressSubView.setStreet(address.getStreet());
        return addressSubView;
    }
}
