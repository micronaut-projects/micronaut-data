package example

import io.micronaut.data.annotation.Join
import jakarta.inject.Inject

import java.util.Optional

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class AuthorRepositoryExample {

    @Inject lateinit var authorRepository: AuthorRepository

    // tag::join-usage[]
    fun joinUsage() {
        // Books will be empty (lazy loading)
        val author = authorRepository.findById("1").orElse(null)

        // Books will be populated (eager loading via @Join)
        val authorWithBooks = authorRepository.searchByName("Stephen King")
    }
    // end::join-usage[]

    // tag::join-fetch[]
    fun fetchOneToMany() {
        // findAll() has @Join("books") so each author's books are eagerly loaded
        val authors = authorRepository.findAll()

        for (author in authors) {
            for (book in author.books) {
                // Access book properties
            }
        }
    }
    // end::join-fetch[]
}
