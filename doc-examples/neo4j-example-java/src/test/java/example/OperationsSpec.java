package example;

import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsSpec extends AbstractNeo4jTest {

    @Inject
    Neo4jRepositoryOperations operations;

    @Inject
    PersonRepository personRepository;

    @AfterEach
    void cleanup() {
        personRepository.deleteAll();
    }

    @Test
    void testExecuteQuery() {
        personRepository.save(new Person("Keanu Reeves", 1964));

        // tag::executeQuery[]
        List<GraphRecord> rows = operations.executeQuery("MATCH (p:Person) RETURN p.name AS name", Map.of()); // <1>
        List<Person> people = operations.executeQuery("MATCH (p:Person) RETURN p", Map.of(), Person.class); // <2>
        // end::executeQuery[]

        assertEquals("Keanu Reeves", rows.get(0).get("name"));
        assertEquals("Keanu Reeves", people.get(0).getName());
    }

    @Test
    void testEncoder() {
        // tag::encode[]
        Money price = new Money(new BigDecimal("9.99"), "EUR");
        List<GraphRecord> rows = operations.executeQuery("RETURN $price.currency AS currency", Map.of("price", price)); // <1>
        // end::encode[]

        assertEquals("EUR", rows.get(0).get("currency"));
    }
}
