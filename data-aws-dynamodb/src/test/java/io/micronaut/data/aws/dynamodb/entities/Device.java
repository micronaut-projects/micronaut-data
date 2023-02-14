package io.micronaut.data.aws.dynamodb.entities;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.aws.dynamodb.annotation.IndexPartitionKey;
import io.micronaut.data.aws.dynamodb.annotation.IndexSortKey;

@MappedEntity("Device")
public class Device {

    @EmbeddedId
    private DeviceId id;

    private String description;

    @IndexPartitionKey(globalSecondaryIndexNames = {"CountryRegionIndex"})
    private String country;

    @IndexSortKey(globalSecondaryIndexNames = {"CountryRegionIndex"})
    private String region;
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

    @Transient
    public Long getVendorId() {
        return (id != null) ? id.getVendorId() : null;
    }

    public void setVendorId(Long vendorId) {
        if (id == null) {
            id = new DeviceId();
        }
        id.setVendorId(vendorId);
    }
}
