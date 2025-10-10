package io.micronaut.data.document.mongodb.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.List;

@MappedEntity
public final class Customer {
    @Id
    @GeneratedValue
    private String id;
    private String firstName;
    private String lastName;

    private List<ChangeLog> changeLogs;

    public Customer() {
    }

    public Customer(String id, String firstName, String lastName, List<ChangeLog> changeLogs) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.changeLogs = changeLogs;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public List<ChangeLog> getChangeLogs() {
        return changeLogs;
    }

    public void setChangeLogs(List<ChangeLog> changeLogs) {
        this.changeLogs = changeLogs;
    }
}
