package io.micronaut.data.r2dbc.h2

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.*
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification

@MicronautTest(transactional = false)
class H2AssignedUuidCascadePersistSpec extends Specification implements H2TestPropertyProvider {

    @Inject R2dbcTenantRepository tenantRepository
    @Inject R2dbcRoleRepository roleRepository

    void "should persist children with assigned UUIDs via cascade persist (R2DBC)"() {
        given:
        def tenantId = UUID.randomUUID()
        def t = new Tenant(id: tenantId, name: 'Acme', host: true, serviceProvider: true)
        (0..<3).each { i ->
            def r = new Role(id: UUID.randomUUID(), name: "Role${i}", description: 'test role')
            t.addRole(r)
        }

        when:
        tenantRepository.save(t).block()

        then:
        tenantRepository.findById(tenantId).block() != null
        roleRepository.countByTenantId(tenantId).block() == 3
    }
}

@MappedEntity("r2_tenant")
class Tenant {
    @Id
    UUID id
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

@MappedEntity("r2_role")
class Role {
    @Id
    UUID id
    String name
    @Nullable
    String description
    @Relation(Relation.Kind.MANY_TO_ONE)
    Tenant tenant
}

@R2dbcRepository(dialect = Dialect.H2)
interface R2dbcTenantRepository extends ReactorCrudRepository<Tenant, UUID> {
}

@R2dbcRepository(dialect = Dialect.H2)
interface R2dbcRoleRepository extends ReactorCrudRepository<Role, UUID> {
    Mono<Long> countByTenantId(UUID tenantId)
}
