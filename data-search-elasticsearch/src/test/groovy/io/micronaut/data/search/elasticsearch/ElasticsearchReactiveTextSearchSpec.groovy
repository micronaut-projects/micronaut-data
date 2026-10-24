package io.micronaut.data.search.elasticsearch

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractReactiveTextSearchSpec
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

class ElasticsearchReactiveTextSearchSpec extends AbstractReactiveTextSearchSpec implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    ElasticsearchContainer elasticsearch = new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")

    @Override
    Map<String, String> getProperties() {
        elasticsearch.start()
        return [
                "elasticsearch.http-hosts": "http://" + elasticsearch.httpHostAddress,
                "micronaut.data.search.index-prefix": "test_"
        ]
    }

    @Memoized
    @Override
    ReactorBookSearchRepository getBookSearchRepository() {
        return context.getBean(ElasticsearchReactorBookSearchRepository)
    }

    @Memoized
    @Override
    AsyncBookSearchRepository getAsyncBookSearchRepository() {
        return context.getBean(ElasticsearchAsyncBookSearchRepository)
    }
}
