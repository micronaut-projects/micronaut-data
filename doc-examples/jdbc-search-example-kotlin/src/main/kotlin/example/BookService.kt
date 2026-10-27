package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.search.MatchMode
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.vector.Vector
import jakarta.inject.Singleton

@Singleton
class BookService(private val bookRepository: BookRepository) {

    fun titlesMatching(query: String): List<String> {
        // tag::search[]
        val results = bookRepository.search(query) // <1>
        for (hit in results) {
            val book = hit.entity() // <2>
            val score = hit.score().value() // <3>
        }
        val hits = bookRepository.countHits(query) // <4>
        // end::search[]
        return results.results().map { it.entity().title }
    }

    fun titlesContainingEveryWord(query: String): List<String> {
        // tag::searchQuery[]
        val searchQuery = SearchQuery.builder(query) // <1>
            .fields("title") // <2>
            .mode(MatchMode.ALL) // <3>
            .build()
        val results = bookRepository.search(searchQuery) // <4>
        val firstPage = bookRepository.search(searchQuery, Pageable.from(0, 10)) // <5>
        // end::searchQuery[]
        return results.results().map { it.entity().title }
    }

    fun hybridTitles(query: String, embedding: Vector): List<String> {
        // tag::hybrid[]
        val searchQuery = SearchQuery.builder(query)
            .vector("embedding", embedding) // <1>
            .textWeight(1.0).vectorWeight(1.0).candidates(50) // <2>
            .build()
        val results = bookRepository.search(searchQuery)
        for (hit in results) {
            val fusedScore = hit.score().value() // <3>
        }
        // end::hybrid[]
        return results.results().map { it.entity().title }
    }

    fun highlightedTitles(query: String): List<String> {
        // tag::highlight[]
        val searchQuery = SearchQuery.builder(query)
            .highlight("title") // <1>
            .build()
        val fragments = bookRepository.search(searchQuery)
            .flatMap { hit -> hit.highlights()["title"] ?: emptyList() } // <2>
        // end::highlight[]
        return fragments
    }
}
