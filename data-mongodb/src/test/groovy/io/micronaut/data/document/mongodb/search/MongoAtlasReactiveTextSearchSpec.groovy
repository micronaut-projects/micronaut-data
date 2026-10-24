package io.micronaut.data.document.mongodb.search

import groovy.transform.Memoized
import io.micronaut.data.search.tck.AbstractReactiveTextSearchSpec

/**
 * Runs the reactive search TCK against MongoDB Search on the Atlas local deployment with the reactive driver.
 */
class MongoAtlasReactiveTextSearchSpec extends AbstractReactiveTextSearchSpec implements MongoAtlasLocal {

    @Override
    String getDriverType() {
        return "reactive"
    }

    @Memoized
    @Override
    def getBookSearchRepository() {
        return context.getBean(MongoAtlasReactorBookSearchRepository)
    }

    @Memoized
    @Override
    def getAsyncBookSearchRepository() {
        return context.getBean(MongoAtlasAsyncBookSearchRepository)
    }

    @Override
    def newBook(String title, String description, String author, int pages) {
        return new AtlasBook(title, description, author, pages)
    }

    @Override
    void awaitIndexing() {
        awaitSearchIndex()
    }
}
