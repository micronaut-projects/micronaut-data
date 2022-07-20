package io.micronaut.data.jdbc.h2.many2many


import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.annotation.JoinColumn
import io.micronaut.data.jdbc.annotation.JoinTable
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class ManyToManyJoinTableUpdateAllSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    StudentRepository2 studentRepository = applicationContext.getBean(StudentRepository2)
    @Shared
    @Inject
    CourseRepository2 courseRepository = applicationContext.getBean(CourseRepository2)

    void 'individual updates should add courses to students'() {
        when:
            Student2 student = new Student2()
            student.setName("Carl")

            Student2 studentPeter = new Student2()
            studentPeter.setName("Peter")

            Course2 course = new Course2()
            course.setTitle("course1")

            def c1 = courseRepository.save(course)

            def studentInserted = studentRepository.save(student)
            def studentInsertedPeter = studentRepository.save(studentPeter)

            def courses = [c1] as Set<Course2>
            studentInserted.setCourses(courses)
            studentInsertedPeter.setCourses(courses)

            studentRepository.update(studentInserted)
            studentRepository.update(studentInsertedPeter)

            def found = studentRepository.findById(studentInserted.id)
            def foundPeter = studentRepository.findById(studentInsertedPeter.id)

        then:
            !found.empty
            !found.get().courses.empty
            !foundPeter.empty
            !foundPeter.get().courses.empty
    }

    void 'single update should add courses to students'() {
        when:
            Student2 student = new Student2()
            student.setName("John")

            Student2 studentJames = new Student2()
            studentJames.setName("James")

            Course2 course = new Course2()
            course.setTitle("course3")

            def c1 = courseRepository.save(course)

            def studentInserted = studentRepository.save(student)
            def studentInsertedJames = studentRepository.save(studentJames)

            def courses = [c1] as Set<Course2>
            studentInserted.setCourses(courses)
            studentInsertedJames.setCourses(courses)

            studentRepository.updateAll([studentInserted, studentInsertedJames])

            def found = studentRepository.findById(studentInserted.id)
            def foundJames = studentRepository.findById(studentInsertedJames.id)

        then:
            !found.empty
            !found.get().courses.empty
            !foundJames.empty
            !foundJames.get().courses.empty
    }

}

@Repository
@JdbcRepository(dialect = Dialect.H2)
interface CourseRepository2 extends CrudRepository<Course2, Long>{
}

@Repository
@JdbcRepository(dialect = Dialect.H2)
interface StudentRepository2 extends CrudRepository<Student2, Long> {

    @Override
    @Join(value = "courses", type = Join.Type.FETCH)
    Optional<Student2> findById(Long id)

}

@MappedEntity("course_updall")
class Course2 {

    @GeneratedValue
    @Id
    Long id

    String title

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    Set<Student2> students

}

@MappedEntity("student_updall")
class Student2 {

    @GeneratedValue
    @Id
    Long id

    String name

    @JoinTable(name = "student_course_updall",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.UPDATE)
    Set<Course2> courses

}
