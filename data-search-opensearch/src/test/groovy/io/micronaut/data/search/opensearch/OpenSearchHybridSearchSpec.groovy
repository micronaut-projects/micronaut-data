package io.micronaut.data.search.opensearch

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractHybridSearchSpec
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository
import io.micronaut.test.support.TestPropertyProvider
import org.opensearch.testcontainers.OpenSearchContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

class OpenSearchHybridSearchSpec extends AbstractHybridSearchSpec implements TestPropertyProvider {

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
    ArticleHybridSearchRepository getArticleRepository() {
        return context.getBean(OpenSearchArticleHybridSearchRepository)
    }
}
