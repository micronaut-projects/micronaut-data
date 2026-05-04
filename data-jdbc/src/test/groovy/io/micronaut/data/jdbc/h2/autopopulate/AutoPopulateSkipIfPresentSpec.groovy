package io.micronaut.data.jdbc.h2.autopopulate

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.jdbc.h2.jakarta_data.simple.Address
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.GenericRepository
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.annotation.Relation.Kind.EMBEDDED

class AutoPopulateSkipIfPresentSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    CustomerRepository customerRepository = applicationContext.getBean(CustomerRepository)

    @Shared
    @Inject
    EmbedUUIDRepository embedUUIDRepository = applicationContext.getBean(EmbedUUIDRepository)

    @Shared
    @Inject
    ImmutableCustomerRepository immutableCustomerRepository = applicationContext.getBean(ImmutableCustomerRepository)

    @Shared
    @Inject
    ImmutableEmbedUUIDRepository immutableEmbedUUIDRepository = applicationContext.getBean(ImmutableEmbedUUIDRepository)

    void 'preset id is not overwritten and id initialized'() {
        when:"Save entity with auto populate value set"
        def preset = UUID.randomUUID()
        def c = new Customer(id: preset, name: "name1", age: 40, address: Address.of("st1","NY","100"), version: null)
        def saved = customerRepository.save(c)
        then:"The value is not overwritten"
        saved.id == preset
        saved.version != null
        when:"Save entity with auto populate value not set"
        c = new Customer(id: null, name: "name2", age: 30, address: Address.of("st2","NJ","100"), version: null)
        saved = customerRepository.save(c)
        then:"The value is generated in the auto populate listener"
        saved.id != null
        cleanup:
        customerRepository.deleteAll()
    }

    void 'preset embedded uuid is preserved when skipIfPresent is true'() {
        when: "Save entity with preset embedded UUID"
        def preset = UUID.randomUUID()
        def embed = new EmbedWithUUID(embId: preset)
        def e = new EntityWithEmbedUUID(id: null, name: "test", embed: embed)
        def saved = embedUUIDRepository.save(e)
        then: "The embedded UUID is preserved"
        saved.embed.embId == preset

        when: "Save entity without preset embedded UUID"
        def e2 = new EntityWithEmbedUUID(id: null, name: "test2", embed: new EmbedWithUUID(embId: null))
        def saved2 = embedUUIDRepository.save(e2)
        then: "The embedded UUID is auto-generated"
        saved2.embed.embId != null

        cleanup:
        embedUUIDRepository.deleteAll()
    }

    void 'preset immutable id is not overwritten and null immutable id is generated'() {
        when:
        def preset = UUID.randomUUID()
        def saved = immutableCustomerRepository.save(new ImmutableCustomer(preset, "immutable-1", null))
        then:
        saved.id == preset
        saved.version != null

        when:
        def generated = immutableCustomerRepository.save(new ImmutableCustomer(null, "immutable-2", null))
        then:
        generated.id != null
        generated.version != null

        cleanup:
        immutableCustomerRepository.deleteAll()
    }

    void 'preset immutable embedded uuid is preserved when skipIfPresent is true'() {
        when:
        def preset = UUID.randomUUID()
        def saved = immutableEmbedUUIDRepository.save(new ImmutableEntityWithEmbedUUID(null, "immutable-embed-1", new ImmutableEmbedWithUUID(preset)))
        then:
        saved.embed.embId == preset

        when:
        def generated = immutableEmbedUUIDRepository.save(new ImmutableEntityWithEmbedUUID(null, "immutable-embed-2", new ImmutableEmbedWithUUID(null)))
        then:
        generated.embed.embId != null

        cleanup:
        immutableEmbedUUIDRepository.deleteAll()
    }
}

@MappedEntity(value = "customer_ap")
class Customer {
    @Id
    @AutoPopulated(skipIfPresent = true)
    UUID id

    String name

    Integer age

    @Relation(EMBEDDED) Address address

    @Version Long version
}

@JdbcRepository(dialect = Dialect.H2)
interface CustomerRepository extends GenericRepository<Customer, UUID> {
    Customer save(Customer entity)

    Optional<Customer> findById(UUID id)

    void deleteAll()
}

@Embeddable
class EmbedWithUUID {
    @AutoPopulated(skipIfPresent = true)
    UUID embId
}

@MappedEntity("entity_embed_uuid")
class EntityWithEmbedUUID {
    @Id
    @AutoPopulated
    UUID id

    String name

    @Relation(EMBEDDED)
    EmbedWithUUID embed
}

@JdbcRepository(dialect = Dialect.H2)
interface EmbedUUIDRepository extends GenericRepository<EntityWithEmbedUUID, UUID> {
    EntityWithEmbedUUID save(EntityWithEmbedUUID entity)

    void deleteAll()
}

@JdbcRepository(dialect = Dialect.H2)
interface ImmutableCustomerRepository extends GenericRepository<ImmutableCustomer, UUID> {
    ImmutableCustomer save(ImmutableCustomer entity)

    void deleteAll()
}

@JdbcRepository(dialect = Dialect.H2)
interface ImmutableEmbedUUIDRepository extends GenericRepository<ImmutableEntityWithEmbedUUID, UUID> {
    ImmutableEntityWithEmbedUUID save(ImmutableEntityWithEmbedUUID entity)

    void deleteAll()
}
