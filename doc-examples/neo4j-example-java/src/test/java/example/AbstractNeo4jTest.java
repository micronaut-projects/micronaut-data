package example;

import io.micronaut.test.support.TestPropertyProvider;

import java.util.Map;

abstract class AbstractNeo4jTest implements TestPropertyProvider {

    @Override
    public Map<String, String> getProperties() {
        return Map.of("neo4j.uri", EmbeddedNeo4j.boltUri());
    }
}
