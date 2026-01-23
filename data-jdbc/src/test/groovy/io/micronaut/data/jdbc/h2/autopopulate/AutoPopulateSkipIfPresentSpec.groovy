package io.micronaut.data.jdbc.h2.autopopulate

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Version
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.jdbc.h2.jakarta_data.simple.Address
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.GenericRepository
import jakarta.data.repository.By
import jakarta.data.repository.Find
import jakarta.data.repository.Insert
import jakarta.data.repository.Repository
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.annotation.Relation.Kind.EMBEDDED
import static jakarta.data.repository.By.ID
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

class AutoPopulateSkipIfPresentSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    CustomerRepository customerRepository = applicationContext.getBean(CustomerRepository)

    void 'preset id is not overwritten and id initialized'() {
        when:"Save entity with auto populate value set"
        def preset = UUID.randomUUID()
        def c = new Customer(id: preset, name: "name1", age: 40, address: Address.of("st1","NY","100"), version: null);
        def saved = customerRepository.save(c)
        then:"The value is not overwritten"
        saved.id == preset
        saved.version != null
        when:"Save entity with auto populate value not set"
        c = new Customer(id: null, name: "name2", age: 30, address: Address.of("st2","NJ","100"), version: null);
        saved = customerRepository.save(c)
        then:"The value is generated in the auto populate listener"
        saved.id != null
        cleanup:
        customerRepository.deleteAll()
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
