package io.micronaut.data.nitrite.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

@MappedEntity
public class CompositeFkParent {

    @Id
    @GeneratedValue
    private String id;

    @MappedProperty("tenant_id")
    private String tenantId;

    @MappedProperty("ref_id")
    private Long refId;

    public CompositeFkParent() {
    }

    @Creator
    public CompositeFkParent(String tenantId, Long refId) {
        this.tenantId = tenantId;
        this.refId = refId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }
}
