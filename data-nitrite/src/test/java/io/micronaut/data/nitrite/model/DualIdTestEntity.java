package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.MappedEntity;
import java.util.UUID;

@MappedEntity("dual_id_test")
public class DualIdTestEntity {

    @io.micronaut.data.annotation.Id
    @org.dizitart.no2.repository.annotations.Id
    private UUID id = UUID.randomUUID();

    private String name;

    public DualIdTestEntity() {}

    public DualIdTestEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
