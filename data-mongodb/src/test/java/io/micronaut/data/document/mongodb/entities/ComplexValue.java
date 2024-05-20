package io.micronaut.data.document.mongodb.entities;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class ComplexValue {

    private String valueA;
    private String valueB;

    public ComplexValue() {
    }

    public ComplexValue(String valueA, String valueB) {
        this.valueA = valueA;
        this.valueB = valueB;
    }

    public String getValueA() {
        return valueA;
    }

    public void setValueA(String valueA) {
        this.valueA = valueA;
    }

    public String getValueB() {
        return valueB;
    }

    public void setValueB(String valueB) {
        this.valueB = valueB;
    }
}
