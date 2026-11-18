package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.Labels;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.DynamicAttributeHolder;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Node
public class Pet implements DynamicAttributeHolder {
    @Id
    private final String name;
    private String species;
    @DynamicAttributes
    private Map<String, Object> attributes = new HashMap<>();
    @Labels
    private Set<String> labels = new LinkedHashSet<>();

    public Pet(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getSpecies() {
        return species;
    }

    public void setSpecies(String species) {
        this.species = species;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    @Override
    public Map<String, Object> dynamicAttributes() {
        return attributes;
    }
}
