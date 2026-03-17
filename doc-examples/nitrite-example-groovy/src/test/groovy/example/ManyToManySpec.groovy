package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

@MicronautTest(transactional = false)
class ManyToManySpec {

    @Inject
    BookRepository bookRepository

    @Inject
    StudentRepository studentRepository

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll()
        studentRepository.deleteAll()
    }

    @Test
    void testManyToManySave() {
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

        // Verify relationships
        def savedBook1 = bookRepository.findById(book1.id).orElse(null)
        assertNotNull(savedBook1)
        assertEquals(1, savedBook1.students.size())

        def savedBook2 = bookRepository.findById(book2.id).orElse(null)
        assertNotNull(savedBook2)
        assertEquals(2, savedBook2.students.size())
    }

    @Test
    void testManyToManyFindStudentWithBooks() {
        // Create students
        def student1 = new Student("Peter")
        def student2 = new Student("Ivone")
        studentRepository.saveAll([student1, student2])

        // Create book with students
        def book = new Book("Pompeii")
        book.students << student1
        book.students << student2
        bookRepository.save(book)

        // Find student by name
        def savedStudent = studentRepository.findByName("Peter")
        assertNotNull(savedStudent)
        assertEquals("Peter", savedStudent.name)
    }
}
