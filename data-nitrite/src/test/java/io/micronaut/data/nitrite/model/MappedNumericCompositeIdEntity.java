package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

@MappedEntity
public class MappedNumericCompositeIdEntity {

    @Id
    @MappedProperty("tenant_key")
    private Long tenantId;

    @Id
    @MappedProperty("sequence_no")
    private Integer sequence;

    private String name;

    public MappedNumericCompositeIdEntity() {
    }

    public MappedNumericCompositeIdEntity(Long tenantId, Integer sequence, String name) {
        this.tenantId = tenantId;
        this.sequence = sequence;
        this.name = name;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
