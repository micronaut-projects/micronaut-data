package io.micronaut.data.jdbc.sqlite.remap;

import io.micronaut.data.jdbc.sqlite.JavaSQLiteDBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@JavaSQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.remap")
class ManyToManyAttributeTest {

    @Inject
    CourseRepository courseRepository;

    @Inject
    StudentRepository studentRepository;

    @Test
    void worksShouldCreateAStudent() {
        Student student = new Student(
            new StudentId(UUID.randomUUID()),
            "test",
            List.of()
        );
        studentRepository.save(student);

        assertEquals(student, studentRepository.findById(student.id()).orElseThrow());
    }

    @Test
    void shouldFindStudentsAttendingACourse() {
        Course course = new Course(
            UUID.randomUUID(),
            "computer science",
            List.of()
        );
        courseRepository.save(course);

        Student student = new Student(
            new StudentId(UUID.randomUUID()),
            "test",
            List.of(course)
        );
        assertEquals(student, studentRepository.save(student));

        assertEquals(student.name(), courseRepository.findStudentsById(course.id()).get(0).name());
    }
}
