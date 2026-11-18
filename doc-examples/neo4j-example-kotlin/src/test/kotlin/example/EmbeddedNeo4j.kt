package example

import io.micronaut.test.support.TestPropertyProvider
import org.neo4j.harness.Neo4j
import org.neo4j.harness.Neo4jBuilders

/**
 * One in-process Neo4j server shared by the tests.
 */
object EmbeddedNeo4j {

    private val server: Neo4j by lazy {
        val neo4j = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build()
        Runtime.getRuntime().addShutdownHook(Thread { neo4j.close() })
        neo4j
    }

    fun boltUri(): String = server.boltURI().toString()
}

abstract class AbstractNeo4jTest : TestPropertyProvider {
    override fun getProperties(): MutableMap<String, String> = mutableMapOf("neo4j.uri" to EmbeddedNeo4j.boltUri())
}
