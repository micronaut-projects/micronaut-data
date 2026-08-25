package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;

@MappedEntity
public class VersionedBook {
    @Id @GeneratedValue Long id;
    String title;
    @Version Long version;

    public VersionedBook(String title) {
        this.title = title;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
