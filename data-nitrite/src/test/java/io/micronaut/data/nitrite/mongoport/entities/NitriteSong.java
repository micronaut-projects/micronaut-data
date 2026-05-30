package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDateTime;

@MappedEntity("nitrite_song")
public class NitriteSong {
    @Id
    private String songHash;
    private String name;
    private LocalDateTime created;
    private LocalDateTime updated;

    public NitriteSong() {
    }

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
