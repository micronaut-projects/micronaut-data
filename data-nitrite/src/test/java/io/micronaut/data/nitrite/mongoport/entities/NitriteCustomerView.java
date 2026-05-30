package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("nitrite_customer_view")
public class NitriteCustomerView {
    private String id;
    private String name;

    public NitriteCustomerView() {
    }

    public NitriteCustomerView(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
