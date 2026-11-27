package example;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

public interface ElasticsearchTestPropertyProvider extends TestPropertyProvider {

    int HTTP_PORT = 9200;

    // A plain container, because ElasticsearchContainer maps a classpath resource that a native test image does not have
    GenericContainer<?> ELASTICSEARCH = new GenericContainer<>(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
        .withExposedPorts(HTTP_PORT)
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node")
        .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
        .waitingFor(Wait.forHttp("/").forPort(HTTP_PORT).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));

    @Override
    default Map<String, String> getProperties() {
        ELASTICSEARCH.start();
        return Map.of("elasticsearch.http-hosts", "http://" + ELASTICSEARCH.getHost() + ":" + ELASTICSEARCH.getMappedPort(HTTP_PORT));
    }
}
