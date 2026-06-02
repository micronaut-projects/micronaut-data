package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@MicronautTest(transactional = false)
class ManyToManyTest {

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var studentRepository: StudentRepository

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
        studentRepository.deleteAll()
    }

    @Test
    fun testManyToManySave() {
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

        // Verify relationships
        val savedBook1 = bookRepository.findById(book1.id!!).orElse(null)
        assertNotNull(savedBook1)
        assertEquals(1, savedBook1!!.students.size)

        val savedBook2 = bookRepository.findById(book2.id!!).orElse(null)
        assertNotNull(savedBook2)
        assertEquals(2, savedBook2!!.students.size)
    }

    @Test
    fun testManyToManyFindStudentWithBooks() {
        // Create students
        val student1 = Student("Peter")
        val student2 = Student("Ivone")
        studentRepository.saveAll(listOf(student1, student2))

        // Create book with students
        val book = Book("Pompeii")
        book.students.add(student1)
        book.students.add(student2)
        bookRepository.save(book)

        // Find student by name
        val savedStudent = studentRepository.findByName("Peter")
        assertNotNull(savedStudent)
        assertEquals("Peter", savedStudent!!.name)
    }
}
