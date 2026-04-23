package io.micronaut.data.jdbc.sqlite.assignedid

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.jdbc.sqlite.SQLiteTestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

import jakarta.validation.constraints.NotBlank

class AssignedUuidManyToManyPersistSpec extends Specification implements SQLiteTestPropertyProvider {

    @Override
    List<String> packages() {
        return Arrays.asList(getClass().package.name)
    }

    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared JdbcStudentRepository studentRepository = ctx.getBean(JdbcStudentRepository)
    @Shared JdbcCourseRepository courseRepository = ctx.getBean(JdbcCourseRepository)

    @PendingFeature(reason = "Cascade update does not remove existing link records, issue https://github.com/micronaut-projects/micronaut-data/issues/3722")
    def "should persist join rows with assigned UUIDs via cascade persist and support update"() {
        given:
        def s = new Student(id: UUID.randomUUID(), name: 'Denis')
        def c1 = new Course(id: UUID.randomUUID(), name: 'Math')
        def c2 = new Course(id: UUID.randomUUID(), name: 'Physics')
        // Ensure child entities exist once; join rows should be created via cascade
        courseRepository.save(c1)
        courseRepository.save(c2)
        s.addCourse(c1)
        s.addCourse(c2)

        when:
        studentRepository.save(s)
        def s2 = studentRepository.findById(s.id).orElse(null)

        then:
        s2 != null
        s2.courses*.id as Set == [c1.id, c2.id] as Set

        when: "associate existing course with new student"
        def s3 = new Student(id: UUID.randomUUID(), name: 'John')
        s3.addCourse(c1)
        studentRepository.save(s3)
        def found = studentRepository.findById(s3.id).orElse(null)

        then:
        found != null
        found.courses*.id as Set == [c1.id] as Set

        when: "update a course and update student"
        c1.name = 'Mathematics'
        s3.courses = [c1]
        studentRepository.update(s3)
        def found2 = studentRepository.findById(s3.id).orElse(null)

        then:
        found2 != null
        found2.courses*.name as Set == ['Mathematics'] as Set
    }
}

@MappedEntity("student_assigned")
class Student {
    @Id
    UUID id
    @NotBlank
    String name
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = [Relation.Cascade.PERSIST, Relation.Cascade.UPDATE])
    List<Course> courses = []
    void addCourse(Course c) {
        if (c != null) {
            courses.add(c)
        }
    }
}

@MappedEntity("course_assigned")
class Course {
    @Id
    UUID id
    @NotBlank
    String name
    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    List<Student> students = []
}

@JdbcRepository(dialect = Dialect.ANSI)
interface JdbcStudentRepository extends CrudRepository<Student, UUID> {
    @Join("courses")
    Optional<Student> findById(UUID id)
}

@JdbcRepository(dialect = Dialect.ANSI)
interface JdbcCourseRepository extends CrudRepository<Course, UUID> {}
