package io.micronaut.data.r2dbc.h2

import io.micronaut.data.annotation.*
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.PendingFeature
import spock.lang.Specification

@MicronautTest(transactional = false)
class H2ManyToManyAssignedIdSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    R2dbcStudentRepository studentRepository
    @Inject
    R2dbcCourseRepository courseRepository

    @PendingFeature(reason = "Cascade update does not remove existing link records, issue https://github.com/micronaut-projects/micronaut-data/issues/3722")
    void "persist and update many-to-many with assigned UUIDs (reactive)"() {
        given:
        def s = new Student(id: UUID.randomUUID(), name: 'Denis')
        def c1 = new Course(id: UUID.randomUUID(), name: 'Math')
        def c2 = new Course(id: UUID.randomUUID(), name: 'Physics')
        // Pre-persist children with assigned IDs; cascade should only write join rows
        courseRepository.save(c1).block()
        courseRepository.save(c2).block()
        s.addCourse(c1)
        s.addCourse(c2)

        when:
        studentRepository.save(s).block()
        def s2 = studentRepository.findById(s.id).block()

        then:
        s2 != null
        s2.courses*.id as Set == [c1.id, c2.id] as Set

        when:
        def s3 = new Student(id: UUID.randomUUID(), name: 'John')
        s3.addCourse(c1)
        studentRepository.save(s3).block()
        def found = studentRepository.findById(s3.id).block()

        then:
        found != null
        found.courses*.id as Set == [c1.id] as Set

        when:
        c1.name = 'Mathematics'
        s3.courses = [c1]
        studentRepository.update(s3).block()
        def found2 = studentRepository.findById(s3.id).block()

        then:
        found2 != null
        found2.courses*.name as Set == ['Mathematics'] as Set
    }
}

@MappedEntity("r2_student_assigned")
class Student {
    @Id
    UUID id
    String name
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = [Relation.Cascade.PERSIST, Relation.Cascade.UPDATE])
    List<Course> courses = []
    void addCourse(Course c) { if (c != null) courses.add(c) }
}

@MappedEntity("r2_course_assigned")
class Course {
    @Id
    UUID id
    String name
    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    List<Student> students = []
}

@R2dbcRepository(dialect = Dialect.H2)
interface R2dbcStudentRepository extends ReactorCrudRepository<Student, UUID> {
    @Join("courses")
    @Override
    Mono<Student> findById(UUID id)
}

@R2dbcRepository(dialect = Dialect.H2)
interface R2dbcCourseRepository extends ReactorCrudRepository<Course, UUID> {
}
