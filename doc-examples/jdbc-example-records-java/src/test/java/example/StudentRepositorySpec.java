package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class StudentRepositorySpec {

    @Inject
    StudentRepository studentRepository;
    @Inject
    CourseRatingRepository courseRatingRepository;

    @Test
    void testCrud() {
        assertNotNull(studentRepository);

        Student student = new Student("Denis", List.of(new Course("Math"), new Course("Physics")));
        student = studentRepository.save(student);

        assertNotNull(student.id());
        assertNotNull(student.courses().get(0).id());
        assertNotNull(student.courses().get(1).id());

        student = studentRepository.findById(student.id()).orElse(null);

        assertNotNull(student.id());
        assertEquals("Denis", student.name());
        assertNotNull(student.courses());
        assertNotNull(student.courses().get(0).id());
        assertEquals("Math", student.courses().get(0).name());
        assertNotNull(student.courses().get(1).id());
        assertEquals("Physics", student.courses().get(1).name());

        CourseRating rating = new CourseRating(student, student.courses().get(1), 5);
        courseRatingRepository.save(rating);

        student = studentRepository.queryById(student.id()).orElse(null);
        assertNotNull(student.id());
        assertEquals("Denis", student.name());
        assertNotNull(student.courses());
        assertNotNull(student.courses().get(0).id());
        assertEquals("Math", student.courses().get(0).name());
        assertNotNull(student.courses().get(1).id());
        assertEquals("Physics", student.courses().get(1).name());
        assertNotNull(student.ratings());
        assertEquals(1, student.ratings().size());
        CourseRating courseRating = student.ratings().iterator().next();
        assertNotNull(courseRating.id());
        assertEquals(student.id(), courseRating.student().id());
        assertEquals("Physics", courseRating.course().name());
        assertEquals(5, courseRating.rating());
    }
}