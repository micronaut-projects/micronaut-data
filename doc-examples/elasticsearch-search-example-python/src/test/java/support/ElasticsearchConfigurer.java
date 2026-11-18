/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package support;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Starts Elasticsearch for the Python tests. A Python test class cannot declare the per-class
 * test instance lifecycle that a {@code TestPropertyProvider} needs, so the container is
 * configured when the application context is built instead.
 */
@ContextConfigurer
public class ElasticsearchConfigurer implements ApplicationContextConfigurer {

    private static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node")
        .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Override
    public void configure(ApplicationContextBuilder builder) {
        // The Elasticsearch client configuration requires the hosts before the context is built
        if (DockerClientFactory.instance().isDockerAvailable()) {
            ELASTICSEARCH.start();
            builder.properties(Map.of("elasticsearch.http-hosts", "http://" + ELASTICSEARCH.getHttpHostAddress()));
        }
    }
}
