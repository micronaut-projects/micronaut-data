package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

@MappedEntity
public class MappedCompositeJoinParent {

    @Id
    @MappedProperty("tenant_key")
    private String tenantId;

    @Id
    @MappedProperty("reference_key")
    private Long refId;

    @MappedProperty("name_key")
    private String name;

    public MappedCompositeJoinParent() {
    }

    public MappedCompositeJoinParent(String tenantId, Long refId, String name) {
        this.tenantId = tenantId;
        this.refId = refId;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
