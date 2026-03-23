package example;

import io.micronaut.data.annotation.Join;
import jakarta.inject.Inject;

import java.util.List;

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
final class AuthorRepositoryExample {

    @Inject AuthorRepository authorRepository;

    // tag::join-usage[]
    void joinUsage() {
        // Books will be empty (lazy loading)
        Author author = authorRepository.findById("1").orElse(null);

        // Books will be populated (eager loading via repository method with @Join)
        Author authorWithBooks = authorRepository.searchByName("Stephen King");
    }
    // end::join-usage[]

    // tag::join-fetch[]
    void fetchOneToMany() {
        // findAll() has @Join("books") so each author's books are eagerly loaded
        List<Author> authors = authorRepository.findAll();

        for (Author author : authors) {
            for (Book book : author.getBooks()) {
                // Access book properties
            }
        }
    }
    // end::join-fetch[]
}
