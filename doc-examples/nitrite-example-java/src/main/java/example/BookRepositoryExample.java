package example;

import jakarta.inject.Inject;

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
final class BookRepositoryExample {

    // tag::inject[]
    @Inject BookRepository bookRepository;
    // end::inject[]

    void crud() {
        // tag::save[]
        Book book = new Book("The Stand");
        bookRepository.save(book);
        // end::save[]

        // tag::read[]
        Book found = bookRepository.findById(book.getId()).orElseThrow();
        // end::read[]

        // tag::update[]
        bookRepository.update(book.getId(), "Changed");
        // end::update[]

        // tag::delete[]
        bookRepository.deleteById(book.getId());
        // end::delete[]
    }
}

