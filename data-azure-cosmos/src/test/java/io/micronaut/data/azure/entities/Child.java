package io.micronaut.data.azure.entities;

import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

import java.util.ArrayList;
import java.util.List;

@Serdeable
public class Child extends GenderAware {

    private String firstName;

    private int grade;

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private List<Pet> pets = new ArrayList<>();

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public List<Pet> getPets() {
        return pets;
    }

    public void setPets(List<Pet> pets) {
        this.pets = pets;
    }
}
