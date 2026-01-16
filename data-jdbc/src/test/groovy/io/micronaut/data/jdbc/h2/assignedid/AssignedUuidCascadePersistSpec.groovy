package io.micronaut.data.jdbc.h2.assignedid

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Id
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import jakarta.annotation.Nullable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.validation.constraints.NotBlank

class AssignedUuidCascadePersistSpec extends Specification implements H2TestPropertyProvider {

    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared TenantRepository tenantRepository = ctx.getBean(TenantRepository)
    @Shared RoleRepository roleRepository = ctx.getBean(RoleRepository)

    def "should persist children with assigned UUIDs via cascade persist"() {
        given:
        def tenantId = UUID.randomUUID()
        def t = new Tenant(id: tenantId, name: 'Acme', host: true, serviceProvider: true)
        3.times { i ->
            def r = new Role(id: UUID.randomUUID(), name: "Role${i}", description: 'test role')
            t.addRole(r)
        }

        when:
        tenantRepository.save(t)

        then:
        tenantRepository.findById(tenantId).present
        roleRepository.countByTenantId(tenantId) == 3
    }

}

@MappedEntity("tenant")
class Tenant {
    @Id
    UUID id
    @NotBlank
    String name
    boolean host
    boolean serviceProvider
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "tenant", cascade = Relation.Cascade.ALL)
    List<Role> roles = []
    void addRole(Role r) {
        if (r != null) {
            r.tenant = this
            roles.add(r)
        }
    }
}

@MappedEntity("role")
class Role {
    @Id
    UUID id
    @NotBlank
    String name
    @Nullable
    String description
    @Relation(Relation.Kind.MANY_TO_ONE)
    Tenant tenant
}

@JdbcRepository(dialect = Dialect.H2)
interface TenantRepository extends CrudRepository<Tenant, UUID> {}

@JdbcRepository(dialect = Dialect.H2)
interface RoleRepository extends CrudRepository<Role, UUID> {
    long countByTenantId(UUID tenantId)
}
