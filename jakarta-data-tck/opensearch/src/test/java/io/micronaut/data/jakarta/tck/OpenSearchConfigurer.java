package io.micronaut.data.jakarta.tck;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Starts one OpenSearch container for the run and points every TCK deployment at it.
 */
public final class OpenSearchConfigurer implements ApplicationContextConfigurer {

    private static final OpenSearchContainer<?> OPENSEARCH = new OpenSearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:3.2.0"));

    @Override
    public void configure(ApplicationContextBuilder builder) {
        OPENSEARCH.start();
        builder.properties(Map.of(
            "micronaut.opensearch.httpclient5.http-hosts", OPENSEARCH.getHttpHostAddress(),
            // each deployment starts from empty indexes
            "micronaut.data.search.schema-generate", "CREATE_DROP"
        ));
    }
}
