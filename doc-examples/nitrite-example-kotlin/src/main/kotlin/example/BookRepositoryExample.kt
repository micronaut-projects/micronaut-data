package example

import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class BookRepositoryExample {

    // tag::inject[]
    @Inject lateinit var bookRepository: BookRepository
    // end::inject[]

    fun crud() {
        // tag::save[]
        val book = bookRepository.save(Book(title = "The Stand"))
        // end::save[]

        // tag::read[]
        val found = bookRepository.findById(book.id!!).orElseThrow()
        // end::read[]

        // tag::update[]
        bookRepository.update(book.id!!, "Changed")
        // end::update[]

        // tag::delete[]
        bookRepository.deleteById(book.id!!)
        // end::delete[]
    }
}

