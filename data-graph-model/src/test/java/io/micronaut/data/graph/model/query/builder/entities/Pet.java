package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.Node;

import java.util.LinkedHashMap;
import java.util.Map;

@Node
public class Pet {
    @Id
    private String name;
    @DynamicAttributes
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
