package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class StudentRepositorySpec {

    @Inject
    StudentRepository studentRepository;

    @Inject
    CourseRepository courseRepository;

    @AfterEach
    public void cleanup() {
        studentRepository.deleteAll().block();
    }

    /**
     * Tests @{@link jakarta.persistence.ManyToMany} relations with Hibernate Reactive.
     */
    @Test
    void testCrud() {
        Course languageCourse = new Course("English");
        languageCourse.setNotes(List.of("Starting in December"));
        courseRepository.save(languageCourse).block();
        Course mathCourse = new Course("Mathematics");
        courseRepository.save(mathCourse).block();

        languageCourse = courseRepository.findById(languageCourse.getId()).block();
        assertNotNull(languageCourse);
        assertEquals(0, languageCourse.getStudents().size());
        assertEquals(1, languageCourse.getNotes().size());

        mathCourse = courseRepository.findById(mathCourse.getId()).block();
        assertNotNull(mathCourse);
        assertEquals(0, mathCourse.getStudents().size());
        assertEquals(0, mathCourse.getNotes().size());

        Student student = new Student("Peter", Set.of(mathCourse));
        studentRepository.save(student).block();
        Long id = student.getId();
        assertNotNull(id);

        student = studentRepository.findById(id).block();
        assertNotNull(student);
        assertEquals("Peter", student.getName());
        assertEquals(1, student.getCourses().size());

        assertEquals(1, studentRepository.count().block());
        assertTrue(studentRepository.findAll().toIterable().iterator().hasNext());

        languageCourse = courseRepository.findById(languageCourse.getId()).block();
        assertNotNull(languageCourse);
        assertEquals(0, languageCourse.getStudents().size());

        mathCourse = courseRepository.findById(mathCourse.getId()).block();
        assertNotNull(mathCourse);
        assertEquals(1, mathCourse.getStudents().size());

        studentRepository.deleteById(id).block();
        assertEquals(0, studentRepository.count().block());
    }

}
