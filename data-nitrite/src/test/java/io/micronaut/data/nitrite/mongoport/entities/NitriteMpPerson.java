package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.ArrayList;
import java.util.List;

@MappedEntity("nitrite_mp_person")
public class NitriteMpPerson {
    @Id
    @GeneratedValue
    private String id;
    private String name;
    private int age;

    private List<NitriteMpAddress> addresses = new ArrayList<>();

    public NitriteMpPerson() {
    }

    public NitriteMpPerson(String name) {
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

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public List<NitriteMpAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<NitriteMpAddress> addresses) {
        this.addresses = addresses;
    }
}
