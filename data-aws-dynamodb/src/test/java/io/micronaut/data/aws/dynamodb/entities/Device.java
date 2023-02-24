package io.micronaut.data.aws.dynamodb.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.aws.dynamodb.annotation.IndexPartitionKey;
import io.micronaut.data.aws.dynamodb.annotation.IndexSortKey;

import java.util.List;
import java.util.Set;

@MappedEntity("Device")
public class Device {

    @EmbeddedId
    private DeviceId id;

    private String description;

    @IndexPartitionKey(globalSecondaryIndexNames = {"CountryRegionIndex"})
    private String country;

    @IndexSortKey(globalSecondaryIndexNames = {"CountryRegionIndex"})
    private String region;

    private boolean enabled;

    private Set<String> notes;

    private Set<Integer> ratings;

    private List<Integer> grades;

    public DeviceId getId() {
        return id;
    }

    public void setId(DeviceId id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getNotes() {
        return notes;
    }

    public void setNotes(Set<String> notes) {
        this.notes = notes;
    }

    public List<Integer> getGrades() {
        return grades;
    }

    public Set<Integer> getRatings() {
        return ratings;
    }

    public void setRatings(Set<Integer> ratings) {
        this.ratings = ratings;
    }

    public void setGrades(List<Integer> grades) {
        this.grades = grades;
    }

    @Transient
    public Long getVendorId() {
        return (id != null) ? id.getVendorId() : null;
    }

    @JsonSetter
    public void setVendorId(Long vendorId) {
        if (id == null) {
            id = new DeviceId();
        }
        id.setVendorId(vendorId);
    }

    @Transient
    public String getProduct() {
        return id != null ? id.getProduct() : null;
    }

    @JsonSetter
    public void setProduct(String product) {
        if (id == null) {
            id = new DeviceId();
        }
        id.setProduct(product);
    }
}
