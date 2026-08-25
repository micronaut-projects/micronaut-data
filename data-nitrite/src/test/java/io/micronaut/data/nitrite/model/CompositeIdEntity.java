package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Entity with multiple {@code @Id} properties (composite identity). Used to
 * exercise the {@code hasCompositeIdentity()} guard in
 * {@code NitritePredicateVisitor.visitIdEquals}.
 */
@MappedEntity
public class CompositeIdEntity {

    @Id
    private String tenantId;

    @Id
    private String refId;

    private String name;

    public CompositeIdEntity() {
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
