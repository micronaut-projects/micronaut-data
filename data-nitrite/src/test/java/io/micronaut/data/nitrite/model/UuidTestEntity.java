package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.util.UUID;

@MappedEntity
public class UuidTestEntity {
    @Id
    @GeneratedValue
    private UUID id;
    private String canonicalName;

    public UuidTestEntity() {}

    public UuidTestEntity(UUID id, String canonicalName) {
        this.id = id;
        this.canonicalName = canonicalName;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCanonicalName() { return canonicalName; }
    public void setCanonicalName(String canonicalName) { this.canonicalName = canonicalName; }
}
