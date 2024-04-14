package example;

import java.io.Serializable;
import java.util.UUID;

public class UUIDTenantId implements Serializable {

    private UUID id;

    private String tenant;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
