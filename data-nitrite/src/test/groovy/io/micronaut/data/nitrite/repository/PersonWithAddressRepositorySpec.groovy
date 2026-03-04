package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.PersonWithAddress
import io.micronaut.data.nitrite.model.PersonWithAddress.Address
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Tests for Gap 2: Nested embedded object field persistence.
 * Note: The annotation processor doesn't support nested property queries (e.g., findByAddressCity).
 * This test verifies that nested objects are properly serialized/deserialized through Nitrite.
 */
@MicronautTest(transactional = false)
class PersonWithAddressRepositorySpec extends Specification {

    @Inject
    PersonWithAddressRepository repository

    def setup() {
        repository.deleteAll()
    }

    void "test save and find with nested address - Gap 2"() {
        given:
        def address = new Address("123 Main St", "New York", "NY", "10001")
        def person = new PersonWithAddress("Alice", 30, address)

        when:
        def saved = repository.save(person)
        def found = repository.findById(saved.id).get()

        then:
        found.name == "Alice"
        found.age == 30
        found.address.city == "New York"
        found.address.state == "NY"
        found.address.street == "123 Main St"
        found.address.zipCode == "10001"
    }

    void "test nested address round-trip - Gap 2"() {
        given:
        def address1 = new Address("123 Main St", "New York", "NY", "10001")
        def address2 = new Address("456 Oak Ave", "Los Angeles", "CA", "90001")
        def person1 = new PersonWithAddress("Alice", 30, address1)
        def person2 = new PersonWithAddress("Bob", 25, address2)

        when:
        def saved1 = repository.save(person1)
        def saved2 = repository.save(person2)
        def found1 = repository.findById(saved1.id).get()
        def found2 = repository.findById(saved2.id).get()

        then:
        found1.address.city == "New York"
        found2.address.city == "Los Angeles"
        found1.address.zipCode == "10001"
        found2.address.zipCode == "90001"
    }

    void "test findByName with nested address present - Gap 2"() {
        given:
        def address = new Address("123 Main St", "New York", "NY", "10001")
        repository.save(new PersonWithAddress("Alice", 30, address))

        when:
        def results = repository.findByName("Alice")

        then:
        results.size() == 1
        results[0].address.city == "New York"
    }

    void "test update preserves nested address - Gap 2"() {
        given:
        def address = new Address("123 Main St", "New York", "NY", "10001")
        def person = repository.save(new PersonWithAddress("Alice", 30, address))

        when:
        person.age = 31
        def updated = repository.update(person)
        def found = repository.findById(person.id).get()

        then:
        found.age == 31
        found.address.city == "New York"  // Address preserved after update
        found.address.street == "123 Main St"
    }
}
