package io.micronaut.data.document.mongodb.upsert.model;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;

@MappedEntity("songs")
public class SongEntity {

    @Id
    String songHash;
    String name;

    @DateCreated
    LocalDateTime created;
    @DateUpdated
    LocalDateTime updated;

    public String getSongHash() {
        return songHash;
    }

    public void setSongHash(String songHash) {
        this.songHash = songHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }
}
