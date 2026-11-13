package io.micronaut.data.search.elasticsearch

import groovy.transform.Memoized
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.search.document.runtime.AsyncSearchTransport
import io.micronaut.data.search.document.runtime.DocumentSearchRepositoryOperations
import io.micronaut.data.search.tck.AbstractReactiveTextSearchSpec
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository
import io.micronaut.test.support.TestPropertyProvider

import java.util.concurrent.CompletionException

import static io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh.WAIT_FOR

class ElasticsearchReactiveTextSearchSpec extends AbstractReactiveTextSearchSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return ElasticsearchTestContainer.properties()
    }

    @Memoized
    @Override
    ReactorBookSearchRepository getBookSearchRepository() {
        return context.getBean(ElasticsearchReactorBookSearchRepository)
    }

    void "an async create of an existing id says the document exists"() {
        given:
        def transport = context.getBean(AsyncSearchTransport)
        def index = context.getBean(DocumentSearchRepositoryOperations).getIndexName(Book)
        def id = UUID.randomUUID().toString()
        transport.create(index, id, [title: "Baking Bread"], WAIT_FOR).toCompletableFuture().join()

        when:
        transport.create(index, id, [title: "Baking Cakes"], WAIT_FOR).toCompletableFuture().join()

        then:
        CompletionException e = thrown()
        e.cause instanceof DataAccessException
        e.cause.message == "A document with id " + id + " already exists in index " + index
    }
}
