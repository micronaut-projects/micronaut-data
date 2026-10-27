package io.micronaut.data.jakarta.tck;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Starts one Elasticsearch container for the run and points every TCK deployment at it.
 */
public final class ElasticsearchConfigurer implements ApplicationContextConfigurer {

    private static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node")
        .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Override
    public void configure(ApplicationContextBuilder builder) {
        ELASTICSEARCH.start();
        builder.properties(Map.of(
            "elasticsearch.http-hosts", "http://" + ELASTICSEARCH.getHttpHostAddress(),
            // each deployment starts from empty indexes
            "micronaut.data.search.schema-generate", "CREATE_DROP"
        ));
    }
}
