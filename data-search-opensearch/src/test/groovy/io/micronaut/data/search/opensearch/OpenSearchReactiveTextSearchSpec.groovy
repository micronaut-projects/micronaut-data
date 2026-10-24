package io.micronaut.data.search.opensearch

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractReactiveTextSearchSpec
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository
import io.micronaut.test.support.TestPropertyProvider
import org.opensearch.testcontainers.OpenSearchContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

class OpenSearchReactiveTextSearchSpec extends AbstractReactiveTextSearchSpec implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    OpenSearchContainer opensearch = new OpenSearchContainer(DockerImageName.parse("opensearchproject/opensearch:3.2.0"))

    @Override
    Map<String, String> getProperties() {
        opensearch.start()
        return [
                "micronaut.opensearch.httpclient5.http-hosts": opensearch.httpHostAddress,
                "micronaut.data.search.index-prefix": "test_"
        ]
    }

    @Memoized
    @Override
    ReactorBookSearchRepository getBookSearchRepository() {
        return context.getBean(OpenSearchReactorBookSearchRepository)
    }

    @Memoized
    @Override
    AsyncBookSearchRepository getAsyncBookSearchRepository() {
        return context.getBean(OpenSearchAsyncBookSearchRepository)
    }
}
