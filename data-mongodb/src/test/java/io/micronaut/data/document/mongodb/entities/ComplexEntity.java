package io.micronaut.data.document.mongodb.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public class ComplexEntity {

    @Id
    @GeneratedValue
    private String id;
    private String simpleValue;

    private ComplexValue complexValue;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSimpleValue() {
        return simpleValue;
    }

    public void setSimpleValue(String simpleValue) {
        this.simpleValue = simpleValue;
    }

    public void setComplexValue(ComplexValue complexValue) {
        this.complexValue = complexValue;
    }

    public ComplexValue getComplexValue() {
        return complexValue;
    }
}
