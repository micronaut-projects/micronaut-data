package example;

import jakarta.inject.Inject;

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
final class BookRepositoryExample {

    // tag::inject[]
    @Inject BookRepository bookRepository;

    @Inject StudentRepository studentRepository;
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

    // tag::cascade-persist[]
    void cascadePersist() {
        Author author = new Author();
        author.setName("Stephen King");

        Book book1 = new Book();
        book1.setTitle("The Stand");
        book1.setAuthor(author);

        Book book2 = new Book();
        book2.setTitle("Pet Cemetery");
        book2.setAuthor(author);

        author.getBooks().add(book1);
        author.getBooks().add(book2);

        // With Cascade.PERSIST, saving the author also saves the books
        // authorRepository.save(author);
    }
    // end::cascade-persist[]

    // tag::manyToMany[]
    void manyToMany() {
        // Create students
        Student student1 = new Student("Peter");
        Student student2 = new Student("Ivone");
        studentRepository.saveAll(java.util.List.of(student1, student2));

        // Create books with students (MANY_TO_MANY)
        Book book1 = new Book("The Roman Triumph");
        book1.getStudents().add(student2);

        Book book2 = new Book("Pompeii");
        book2.getStudents().add(student1);
        book2.getStudents().add(student2);

        bookRepository.saveAll(java.util.List.of(book1, book2));
    }
    // end::manyToMany[]

    // tag::batch-operations[]
    void batchOperations() {
        java.util.List<Book> books = java.util.List.of(
            new Book("Book 1"),
            new Book("Book 2"),
            new Book("Book 3")
        );
        bookRepository.saveAll(books); // Single batch operation
    }
    // end::batch-operations[]
}

