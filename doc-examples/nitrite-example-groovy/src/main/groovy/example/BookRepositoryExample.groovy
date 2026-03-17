package example

import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class BookRepositoryExample {

    // tag::inject[]
    @Inject BookRepository bookRepository

    @Inject StudentRepository studentRepository
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

    // tag::cascade-persist[]
    void cascadePersist() {
        def author = new Author(name: "Stephen King")

        def book1 = new Book(title: "The Stand")
        book1.author = author

        def book2 = new Book(title: "Pet Cemetery")
        book2.author = author

        author.books << book1
        author.books << book2

        // With Cascade.PERSIST, saving the author also saves the books
        // authorRepository.save(author)
    }
    // end::cascade-persist[]

    // tag::manyToMany[]
    void manyToMany() {
        // Create students
        def student1 = new Student("Peter")
        def student2 = new Student("Ivone")
        studentRepository.saveAll([student1, student2])

        // Create books with students (MANY_TO_MANY)
        def book1 = new Book("The Roman Triumph")
        book1.students << student2

        def book2 = new Book("Pompeii")
        book2.students << student1
        book2.students << student2

        bookRepository.saveAll([book1, book2])
    }
    // end::manyToMany[]

    // tag::batch-operations[]
    void batchOperations() {
        def books = [
            new Book("Book 1"),
            new Book("Book 2"),
            new Book("Book 3")
        ]
        bookRepository.saveAll(books) // Single batch operation
    }
    // end::batch-operations[]
}

