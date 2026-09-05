package example;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

// tag::composite-fk-parent[]
@MappedEntity
public class CompositeFkParent {
    @Id
    @GeneratedValue
    private String id;

    private String tenantId;
    private Long refId;

    @Creator
    public CompositeFkParent(String tenantId, Long refId) {
        this.tenantId = tenantId;
        this.refId = refId;
    }
    // end::composite-fk-parent[]

    public CompositeFkParent() {
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
// tag::composite-fk-parent[]
}
// end::composite-fk-parent[]
