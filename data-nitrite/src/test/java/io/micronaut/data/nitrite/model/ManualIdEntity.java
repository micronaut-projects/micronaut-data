package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.UUID;

/** Entity with a manually assigned UUID ID (or defaulted). */
@MappedEntity("manual_ids")
public class ManualIdEntity {
    @Id
    private UUID id = UUID.randomUUID();

    private String name;

    public ManualIdEntity() {}

    public ManualIdEntity(String name) {
        this.name = name;
    }

    public ManualIdEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
