package example

import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class BookRepositoryExample {

    // tag::inject[]
    @Inject lateinit var bookRepository: BookRepository

    @Inject lateinit var studentRepository: StudentRepository
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

    // tag::cascade-persist[]
    fun cascadePersist() {
        val author = Author(name = "Stephen King")

        val book1 = Book(title = "The Stand")
        book1.author = author

        val book2 = Book(title = "Pet Cemetery")
        book2.author = author

        author.books.add(book1)
        author.books.add(book2)

        // With Cascade.PERSIST, saving the author also saves the books
        // authorRepository.save(author)
    }
    // end::cascade-persist[]

    // tag::manyToMany[]
    fun manyToMany() {
        // Create students
        val student1 = Student("Peter")
        val student2 = Student("Ivone")
        studentRepository.saveAll(listOf(student1, student2))

        // Create books with students (MANY_TO_MANY)
        val book1 = Book("The Roman Triumph")
        book1.students.add(student2)

        val book2 = Book("Pompeii")
        book2.students.add(student1)
        book2.students.add(student2)

        bookRepository.saveAll(listOf(book1, book2))
    }
    // end::manyToMany[]

    // tag::batch-operations[]
    fun batchOperations() {
        val books = listOf(
            Book("Book 1"),
            Book("Book 2"),
            Book("Book 3")
        )
        bookRepository.saveAll(books) // Single batch operation
    }
    // end::batch-operations[]
}

