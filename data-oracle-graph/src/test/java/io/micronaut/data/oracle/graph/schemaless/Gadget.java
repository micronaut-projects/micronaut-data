package io.micronaut.data.oracle.graph.schemaless;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.DynamicAttributeHolder;

import java.util.LinkedHashMap;
import java.util.Map;

@Node
public class Gadget implements DynamicAttributeHolder {

    @Id
    private final String name;

    private final int price;

    @DynamicAttributes
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public Gadget(String name, int price) {
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> dynamicAttributes() {
        return attributes;
    }
}
