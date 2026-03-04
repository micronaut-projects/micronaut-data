package example

import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class BookRepositoryExample {

    // tag::inject[]
    @Inject BookRepository bookRepository
    // end::inject[]

    void crud() {
        // tag::save[]
        def book = bookRepository.save(new Book("The Stand"))
        // end::save[]

        // tag::read[]
        def found = bookRepository.findById(book.id).orElse(null)
        // end::read[]

        // tag::update[]
        bookRepository.update(book.id, "Changed")
        // end::update[]

        // tag::delete[]
        bookRepository.deleteById(book.id)
        // end::delete[]
    }
}

