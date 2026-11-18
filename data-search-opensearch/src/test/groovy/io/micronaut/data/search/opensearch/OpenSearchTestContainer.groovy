package io.micronaut.data.search.opensearch

import org.opensearch.testcontainers.OpenSearchContainer
import org.testcontainers.utility.DockerImageName

/**
 * One OpenSearch container for every spec of the JVM, started on first use. A {@code @Shared}
 * field cannot be used, as the TCK base class starts the application context while the subclass
 * fields are still null.
 */
class OpenSearchTestContainer {

    private static OpenSearchContainer container

    static synchronized String httpHostAddress() {
        if (container == null) {
            container = new OpenSearchContainer(DockerImageName.parse("opensearchproject/opensearch:3.2.0"))
            container.start()
        }
        return container.httpHostAddress
    }

    static Map<String, String> properties() {
        return [
                "micronaut.opensearch.httpclient5.http-hosts": httpHostAddress(),
                "micronaut.data.search.index-prefix"          : "test_"
        ]
    }
}
