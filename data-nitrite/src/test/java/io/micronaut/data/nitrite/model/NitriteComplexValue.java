package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class NitriteComplexValue {
    private String key;
    private String data;

    public NitriteComplexValue() {
    }

    public NitriteComplexValue(String key, String data) {
        this.key = key;
        this.data = data;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
