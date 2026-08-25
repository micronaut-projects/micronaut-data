package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@MappedEntity
@Serdeable
public class MapEntity {
    @Id
    @GeneratedValue
    private Long id;
    private Map<String, NestedPojo> data;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Map<String, NestedPojo> getData() {
        return data;
    }

    public void setData(Map<String, NestedPojo> data) {
        this.data = data;
    }
}
