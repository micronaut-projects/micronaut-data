package example

import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Specification

abstract class AbstractNeo4jSpec extends Specification implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }
}
