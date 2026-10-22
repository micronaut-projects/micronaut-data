package io.micronaut.data.search.opensearch

import groovy.transform.Memoized
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.repositories.BookSearchRepository
import io.micronaut.test.support.TestPropertyProvider
import org.opensearch.testcontainers.OpenSearchContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared

class OpenSearchTextSearchSpec extends AbstractTextSearchSpec implements TestPropertyProvider {

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
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(OpenSearchBookSearchRepository)
    }

    @Override
    Set<SearchCapability> capabilities() {
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.PREFIX, SearchCapability.FUZZY, SearchCapability.FIELD_SCOPE, SearchCapability.BOOST)
    }
}
