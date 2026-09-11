package example

import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class AuthorRepositoryExample {

    @Inject AuthorRepository authorRepository

    // tag::join-usage[]
    void joinUsage() {
        // Books will be empty (lazy loading)
        def author = authorRepository.findById("1").orElse(null)

        // Books will be populated (eager loading via repository method with @Join)
        def authorWithBooks = authorRepository.searchByName("Stephen King")
    }
    // end::join-usage[]

    // tag::join-fetch[]
    void fetchOneToMany() {
        // findAll() has @Join("books") so each author's books are eagerly loaded
        def authors = authorRepository.findAll()

        for (author in authors) {
            for (book in author.books) {
                // Access book properties
            }
        }
    }
    // end::join-fetch[]
}
