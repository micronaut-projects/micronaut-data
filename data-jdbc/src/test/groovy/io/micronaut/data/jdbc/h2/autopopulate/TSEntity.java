package io.micronaut.data.jdbc.h2.autopopulate;

import io.micronaut.data.annotation.*;

import java.time.Instant;
import java.util.UUID;

@MappedEntity("ts_skip_ap")
public class TSEntity {
    @Id
    @AutoPopulated
    private UUID id;

    @DateCreated(skipIfPresent = true)
    private Instant dateCreated;

    @DateUpdated(skipIfPresent = true)
    private Instant dateUpdated;

    public TSEntity() {}

    public TSEntity(UUID id, Instant dateCreated, Instant dateUpdated) {
        this.id = id;
        this.dateCreated = dateCreated;
        this.dateUpdated = dateUpdated;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Instant getDateCreated() { return dateCreated; }
    public void setDateCreated(Instant dateCreated) { this.dateCreated = dateCreated; }

    public Instant getDateUpdated() { return dateUpdated; }
    public void setDateUpdated(Instant dateUpdated) { this.dateUpdated = dateUpdated; }
}
