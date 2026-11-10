package example

import groovy.transform.CompileStatic
import org.neo4j.harness.Neo4j
import org.neo4j.harness.Neo4jBuilders

/**
 * One in-process Neo4j server shared by the specs.
 */
@CompileStatic
class EmbeddedNeo4j {

    private static Neo4j server

    static synchronized String boltUri() {
        if (server == null) {
            server = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build()
            Runtime.runtime.addShutdownHook(new Thread({ server.close() }))
        }
        server.boltURI().toString()
    }
}
