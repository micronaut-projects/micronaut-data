package io.micronaut.data.jdbc.sqlite.assignedid;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignedUuidCascadePersistTest {

    @Test
    void shouldPersistChildrenWithAssignedUuidsViaCascadePersist() {
        try (ApplicationContext ctx = ApplicationContext.run(createProperties())) {
            TenantRepository tenantRepository = ctx.getBean(TenantRepository.class);
            RoleRepository roleRepository = ctx.getBean(RoleRepository.class);

            UUID tenantId = UUID.randomUUID();
            Tenant tenant = new Tenant();
            tenant.setId(tenantId);
            tenant.setName("Acme");
            tenant.setHost(true);
            tenant.setServiceProvider(true);
            for (int i = 0; i < 3; i++) {
                Role role = new Role();
                role.setId(UUID.randomUUID());
                role.setName("Role" + i);
                role.setDescription("test role");
                tenant.addRole(role);
            }

            tenantRepository.save(tenant);

            assertTrue(tenantRepository.findById(tenantId).isPresent());
            assertEquals(3, roleRepository.countByTenantId(tenantId));
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("assigneduuidcascadepersist", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "ANSI");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite.assignedid");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@MappedEntity("tenant")
class Tenant {

    @Id
    private UUID id;

    @NotBlank
    private String name;

    private boolean host;
    private boolean serviceProvider;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "tenant", cascade = Relation.Cascade.ALL)
    private List<Role> roles = new ArrayList<>();

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    boolean isHost() {
        return host;
    }

    void setHost(boolean host) {
        this.host = host;
    }

    boolean isServiceProvider() {
        return serviceProvider;
    }

    void setServiceProvider(boolean serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    List<Role> getRoles() {
        return roles;
    }

    void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    void addRole(Role role) {
        if (role != null) {
            role.setTenant(this);
            roles.add(role);
        }
    }
}

@MappedEntity("role")
class Role {

    @Id
    private UUID id;

    @NotBlank
    private String name;

    @Nullable
    private String description;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Tenant tenant;

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    @Nullable
    String getDescription() {
        return description;
    }

    void setDescription(@Nullable String description) {
        this.description = description;
    }

    Tenant getTenant() {
        return tenant;
    }

    void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface TenantRepository extends CrudRepository<Tenant, UUID> {
}

@JdbcRepository(dialect = Dialect.ANSI)
interface RoleRepository extends CrudRepository<Role, UUID> {
    long countByTenantId(UUID tenantId);
}
