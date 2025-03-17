package io.micronaut.data.jdbc.h2.remap

import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@H2DBProperties
@MicronautTest
class ManyToManyAttributeSpec extends Specification {

    @Inject
    CourseRepository courseRepository
    @Inject
    StudentRepository studentRepository

    def "works - should create a student"() {
        when:
            Student student = new Student(
                    new StudentId(UUID.randomUUID()),
                    "test",
                    List.of()
            )
            studentRepository.save(student)
        then:
            studentRepository.findById(student.id()).get() == student
    }

    def "should find students attending a course"() {
        when:
            Course course = new Course(
                    UUID.randomUUID(),
                    "computer science",
                    List.of()
            )
            courseRepository.save(course)
            // create a new student and join the existing course
            Student student = new Student(
                    new StudentId(UUID.randomUUID()),
                    "test",
                    List.of(course)
            )
            studentRepository.save(student) == student

        then:
            // we should now be able to find the student that attends the course
            courseRepository.findStudentsById(course.id())[0].name() == student.name()
    }
}
