package com.example.entity;

import jakarta.persistence.*;

import java.util.Map;

@Entity
public class EntityWithMapField {

    @Id
    private Long id;

    @ElementCollection
    @CollectionTable(name = "client_properties", joinColumns = @JoinColumn(name = "client_id"))
    @MapKeyColumn(name = "prop_key")
    @Column(name = "prop_value")
    private Map<String, String> properties;

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
