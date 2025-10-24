package io.micronaut.data.runtime.criteria;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;

@Embeddable
public class TestCustomerId {
    @MappedProperty("id")
    private String id;

    @MappedProperty("name")
    private String name;

    @MappedProperty("version")
    private String version;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
