package io.micronaut.data.runtime.criteria;

import io.micronaut.data.annotation.Relation;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.util.Map;

@Entity
class MapOwnerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private Map<String, MapValueEntity> attributes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Map<String, MapValueEntity> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, MapValueEntity> attributes) {
        this.attributes = attributes;
    }
}
