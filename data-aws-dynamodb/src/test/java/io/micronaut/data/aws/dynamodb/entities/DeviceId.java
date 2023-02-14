package io.micronaut.data.aws.dynamodb.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.aws.dynamodb.annotation.PartitionKey;
import io.micronaut.data.aws.dynamodb.annotation.SortKey;

@Embeddable
public class DeviceId {

    @PartitionKey
    @MappedProperty("vendorId")
    private Long vendorId;

    @SortKey
    @MappedProperty("product")
    private String product;

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }
}
