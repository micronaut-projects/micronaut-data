package example;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public interface ElasticsearchTestPropertyProvider extends TestPropertyProvider {

    ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node")
        .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Override
    default Map<String, String> getProperties() {
        ELASTICSEARCH.start();
        return Map.of("elasticsearch.http-hosts", "http://" + ELASTICSEARCH.getHttpHostAddress());
    }
}
