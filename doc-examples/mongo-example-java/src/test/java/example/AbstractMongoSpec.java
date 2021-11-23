package example;

import io.micronaut.test.support.TestPropertyProvider;
import org.bson.UuidRepresentation;
import org.junit.After;
import org.junit.Rule;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMongoSpec implements TestPropertyProvider {

    @Rule
    MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo").withTag("5"));

    @After
    protected void after() {
        mongoDBContainer.stop();
    }

    @Override
    public Map<String, String> getProperties() {
        mongoDBContainer.start();
        Map<String, String> m = new HashMap<>();
        m.put("mongodb.uri", mongoDBContainer.getReplicaSetUrl());
        m.put("mongodb.uuid-representation", UuidRepresentation.STANDARD.name());
        return m;
    }
}
