package io.micronaut.data.search.elasticsearch

import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName

/**
 * One Elasticsearch container for every spec of the JVM, started on first use. A {@code @Shared}
 * field cannot be used, as the TCK base class starts the application context while the subclass
 * fields are still null.
 */
class ElasticsearchTestContainer {

    private static ElasticsearchContainer container

    static synchronized String httpHostAddress() {
        if (container == null) {
            container = new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            container.start()
        }
        return "http://" + container.httpHostAddress
    }

    static Map<String, String> properties() {
        return [
                "elasticsearch.http-hosts"          : httpHostAddress(),
                "micronaut.data.search.index-prefix": "test_"
        ]
    }
}
