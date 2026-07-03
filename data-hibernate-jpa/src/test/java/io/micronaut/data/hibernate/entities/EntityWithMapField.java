package io.micronaut.data.hibernate.entities;


import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OrderColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
public class EntityWithMapField {

    @Id
    private Long id;

    @ElementCollection
    @CollectionTable(name = "client_properties", joinColumns = @JoinColumn(name = "client_id"))
    @MapKeyColumn(name = "prop_key")
    @Column(name = "prop_value")
    private Map<String, String> properties;

    @ElementCollection
    @CollectionTable(name = "client_tags_list", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "tag")
    @OrderColumn(name = "pos")
    private List<String> tagsList = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "client_tags_set", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "tag")
    private Set<String> tagsSet = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "client_tags_collection", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "tag")
    private Collection<String> tagsCollection = new ArrayList<>();


    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public List<String> getTagsList() {
        return tagsList;
    }

    public void setTagsList(List<String> tagsList) {
        this.tagsList = tagsList;
    }

    public Set<String> getTagsSet() {
        return tagsSet;
    }

    public void setTagsSet(Set<String> tagsSet) {
        this.tagsSet = tagsSet;
    }

    public Collection<String> getTagsCollection() {
        return tagsCollection;
    }

    public void setTagsCollection(Collection<String> tagsCollection) {
        this.tagsCollection = tagsCollection;
    }
}
