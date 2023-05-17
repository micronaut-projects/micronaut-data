package example

import io.micronaut.test.support.TestPropertyProvider
import org.bson.UuidRepresentation
import org.junit.After
import org.junit.Rule
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

abstract class AbstractMongoSpec : TestPropertyProvider {
    @Rule
    var mongoDBContainer1 = MongoDBContainer(DockerImageName.parse("mongo").withTag("5"))
    @Rule
    var mongoDBContainer2 = MongoDBContainer(DockerImageName.parse("mongo").withTag("5"))

    @After
    fun after() {
        mongoDBContainer1.stop()
        mongoDBContainer2.stop()
    }

    override fun getProperties(): Map<String, String> {
        mongoDBContainer1.start()
        return mapOf(
                "mongodb.uri" to mongoDBContainer1.replicaSetUrl,
                "mongodb.uuid-representation" to UuidRepresentation.STANDARD.name
        )
    }

    fun getPropertiesForDefaultCustomDB(): Map<String, String> {
        mongoDBContainer1.start()
        return mapOf(
                "mongodb.servers.default.uri" to mongoDBContainer1.replicaSetUrl,
                "mongodb.servers.default.uuid-representation" to UuidRepresentation.STANDARD.name
        )
    }

    fun getPropertiesForCustomDB(): Map<String, String> {
        mongoDBContainer2.start()
        return mapOf(
                "mongodb.servers.custom.uri" to mongoDBContainer2.replicaSetUrl,
                "mongodb.servers.custom.uuid-representation" to UuidRepresentation.STANDARD.name
        )
    }
}