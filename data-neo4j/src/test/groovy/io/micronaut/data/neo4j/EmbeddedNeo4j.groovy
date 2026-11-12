package io.micronaut.data.neo4j

import groovy.transform.CompileStatic
import org.neo4j.harness.Neo4j
import org.neo4j.harness.Neo4jBuilders

/**
 * One in-process Neo4j server shared by the specs of this module.
 */
@CompileStatic
class EmbeddedNeo4j {

    private static Neo4j server

    static synchronized String boltUri() {
        if (server == null) {
            server = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .build()
            Runtime.runtime.addShutdownHook(new Thread({ server.close() }))
        }
        return server.boltURI().toString()
    }
}
