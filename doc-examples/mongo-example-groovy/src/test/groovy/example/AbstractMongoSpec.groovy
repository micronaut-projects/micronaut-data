package example

import io.micronaut.test.support.TestPropertyProvider
import org.bson.UuidRepresentation
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractMongoSpec extends Specification implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo").withTag("5"))

    @Override
    Map<String, String> getProperties() {
        mongoDBContainer.start()
        Map<String, String> m = new HashMap<>()
        m.put( "mongodb.uri", mongoDBContainer.getReplicaSetUrl())
        m.put( "mongodb.uuid-representation", UuidRepresentation.STANDARD.name())
        return m
    }
}
