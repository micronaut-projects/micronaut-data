package example

import io.micronaut.test.support.TestPropertyProvider
import org.bson.UuidRepresentation
import org.junit.After
import org.junit.Rule
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

abstract class AbstractMongoSpec : TestPropertyProvider {
    @Rule
    var mongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo").withTag("5"))

    @After
    fun after() {
        mongoDBContainer.stop()
    }

    override fun getProperties(): Map<String, String> {
        mongoDBContainer.start()
        return mapOf(
                "mongodb.uri" to mongoDBContainer.replicaSetUrl,
                "mongodb.uuid-representation" to UuidRepresentation.STANDARD.name
        )
    }
}