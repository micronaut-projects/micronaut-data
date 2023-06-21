package io.micronaut.data.document.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.List;
import java.util.Map;

@MappedEntity
public class Document {

    @Id
    @GeneratedValue
    private String id;

    private String title;

    private List<String> tags;

    private Map<String, Owner> owners;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Owner> getOwners() {
        return owners;
    }

    public void setOwners(Map<String, Owner> owners) {
        this.owners = owners;
    }
}
