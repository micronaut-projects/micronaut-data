package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class ManyToManySpec {

    @Inject
    BookRepository bookRepository;

    @Inject
    StudentRepository studentRepository;

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
        studentRepository.deleteAll();
    }

    @Test
    void testManyToManySave() {
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

        // Verify relationships
        Book savedBook1 = bookRepository.findById(book1.getId()).orElse(null);
        assertNotNull(savedBook1);
        assertEquals(1, savedBook1.getStudents().size());

        Book savedBook2 = bookRepository.findById(book2.getId()).orElse(null);
        assertNotNull(savedBook2);
        assertEquals(2, savedBook2.getStudents().size());
    }

    @Test
    void testManyToManyFindStudentWithBooks() {
        // Create students
        Student student1 = new Student("Peter");
        Student student2 = new Student("Ivone");
        studentRepository.saveAll(java.util.List.of(student1, student2));

        // Create book with students
        Book book = new Book("Pompeii");
        book.getStudents().add(student1);
        book.getStudents().add(student2);
        bookRepository.save(book);

        // Find student by name
        Student savedStudent = studentRepository.findByName("Peter");
        assertNotNull(savedStudent);
        assertEquals("Peter", savedStudent.getName());
    }
}
