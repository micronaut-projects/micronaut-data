package io.micronaut.data.r2dbc.oraclexe.search

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.oraclexe.Oracle23TestPropertyProvider
import io.micronaut.data.search.tck.AbstractReactiveTextSearchSpec
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository

class OracleReactiveTextSearchSpec extends AbstractReactiveTextSearchSpec implements Oracle23TestPropertyProvider {

    @Memoized
    @Override
    ReactorBookSearchRepository getBookSearchRepository() {
        return context.getBean(OracleReactorBookSearchRepository)
    }

    @Memoized
    @Override
    AsyncBookSearchRepository getAsyncBookSearchRepository() {
        return context.getBean(OracleAsyncBookSearchRepository)
    }

    @Override
    List<String> packages() {
        return [getClass().package.name, "io.micronaut.data.search.tck.entities"]
    }
}
