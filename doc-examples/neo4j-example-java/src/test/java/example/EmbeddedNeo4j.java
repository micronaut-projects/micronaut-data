package example;

import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

/**
 * One in-process Neo4j server shared by the tests.
 */
final class EmbeddedNeo4j {

    private static Neo4j server;

    private EmbeddedNeo4j() {
    }

    static synchronized String boltUri() {
        if (server == null) {
            server = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        }
        return server.boltURI().toString();
    }
}
