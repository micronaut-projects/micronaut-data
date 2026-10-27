package example

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
}
