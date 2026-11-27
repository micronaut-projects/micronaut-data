package io.micronaut.data.neo4j

import io.micronaut.data.neo4j.features.Composer
import io.micronaut.data.neo4j.features.ComposerRepository
import io.micronaut.data.neo4j.features.Conductor
import io.micronaut.data.neo4j.features.ConductorRepository
import io.micronaut.data.neo4j.features.Critic
import io.micronaut.data.neo4j.features.CriticRepository
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * The naming strategy an application configures for its JSON does not apply to what a node holds.
 */
@MicronautTest(transactional = false)
class Neo4jGlobalSerdeNamingSpec extends Specification implements TestPropertyProvider {

    @Inject
    Neo4jRepositoryOperations operations

    @Inject
    ConductorRepository conductorRepository

    @Inject
    ComposerRepository composerRepository

    @Inject
    CriticRepository criticRepository

    @Inject
    ObjectMapper json

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.serde.property-naming-strategy': 'SNAKE_CASE']
    }

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "a class read by the deserializer serde builds at runtime keeps its properties, and JSON still uses the strategy"() {
        given:
        def conductor = new Conductor("Marin Alsop")
        conductor.birthYear = 1956

        when:
        conductorRepository.save(conductor)
        def found = conductorRepository.findById("Marin Alsop").get()

        then:
        found.fullName == "Marin Alsop"
        found.birthYear == 1956

        and: "JSON of the same class follows the strategy, after and before the graph read"
        json.writeValueAsString(found).contains('"birth_year":1956')
        json.readValue('{"full_name":"Marin Alsop","birth_year":1956}', Conductor).birthYear == 1956
        conductorRepository.findById("Marin Alsop").get().birthYear == 1956
    }

    void "a record keeps its properties, under a data naming strategy and without one"() {
        given:
        def composer = new Composer("Arvo Part", 1935, 200)
        def critic = new Critic("Pauline Kael", 12)

        when:
        composerRepository.save(composer)
        criticRepository.save(critic)

        then:
        composerRepository.findById("Arvo Part").get() == composer
        criticRepository.findById("Pauline Kael").get() == critic
    }
}
