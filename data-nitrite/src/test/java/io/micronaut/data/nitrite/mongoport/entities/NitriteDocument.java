package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@MappedEntity("nitrite_document")
public class NitriteDocument {

    @Id
    @GeneratedValue
    @Nullable
    private String id;

    @Nullable
    private String title;

    @Nullable
    private List<String> tags;

    @Nullable
    private Map<String, NitriteDocumentOwner> owners;

    @Nullable
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Nullable
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    @Nullable
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    @Nullable
    public Map<String, NitriteDocumentOwner> getOwners() { return owners; }
    public void setOwners(Map<String, NitriteDocumentOwner> owners) { this.owners = owners; }
}
