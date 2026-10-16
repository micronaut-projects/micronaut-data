package example

import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class OperationsSpec extends AbstractNeo4jSpec {

    @Inject
    Neo4jRepositoryOperations operations

    @Inject
    PersonRepository personRepository

    void cleanup() {
        personRepository.deleteAll()
    }

    void "execute queries"() {
        given:
        personRepository.save(new Person(name: "Keanu Reeves", born: 1964))

        when:
        // tag::executeQuery[]
        def rows = operations.executeQuery('MATCH (p:Person) RETURN p.name AS name', [:]) // <1>
        def people = operations.executeQuery('MATCH (p:Person) RETURN p', [:], Person) // <2>
        // end::executeQuery[]

        then:
        rows[0].get("name") == "Keanu Reeves"
        people[0].name == "Keanu Reeves"
    }

    void "encode application types"() {
        when:
        // tag::encode[]
        def price = new Money(amount: 9.99, currency: "EUR")
        def rows = operations.executeQuery('RETURN $price.currency AS currency', [price: price]) // <1>
        // end::encode[]

        then:
        rows[0].get("currency") == "EUR"
    }
}
