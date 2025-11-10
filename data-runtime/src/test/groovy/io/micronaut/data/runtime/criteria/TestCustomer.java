package io.micronaut.data.runtime.criteria;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

@MappedEntity("CUSTOMER")
public class TestCustomer {

    @EmbeddedId
    @MappedProperty("id")
    private TestCustomerId id;

    private String address;

    public TestCustomerId getId() {
        return id;
    }

    public void setId(TestCustomerId id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
