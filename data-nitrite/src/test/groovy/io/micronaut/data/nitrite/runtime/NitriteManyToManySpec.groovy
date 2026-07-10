package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.NitriteMtmCourse
import io.micronaut.data.nitrite.model.NitriteMtmStudent
import io.micronaut.data.nitrite.repository.NitriteMtmStudentRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteManyToManySpec extends Specification implements NitriteTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMtmStudentRepository studentRepository = applicationContext.getBean(NitriteMtmStudentRepository)

    def cleanup() {
        studentRepository.deleteAll()
    }

    void 'test many-to-many relationship'() {
        given:
            NitriteMtmCourse course1 = new NitriteMtmCourse(title: "Math")
            NitriteMtmCourse course2 = new NitriteMtmCourse(title: "Science")

            NitriteMtmStudent student1 = new NitriteMtmStudent(name: "Alice")
            NitriteMtmStudent student2 = new NitriteMtmStudent(name: "Bob")

            student1.courses = [course1, course2]
            student2.courses = [course1]

            course1.students = [student1, student2]
            course2.students = [student1]

        when:
            studentRepository.save(student1)
            studentRepository.save(student2)

            NitriteMtmStudent saved = studentRepository.findById(student1.id).get()

        then:
            saved.id
            saved.name == "Alice"
            saved.courses.size() == 2
            saved.courses[0].title == "Math"
            saved.courses[1].title == "Science"

        when:
            NitriteMtmStudent savedStudent2 = studentRepository.findById(student2.id).get()

        then:
            savedStudent2.name == "Bob"
            savedStudent2.courses.size() == 1
            savedStudent2.courses[0].title == "Math"
    }

    void 'test many-to-many update'() {
        given:
            NitriteMtmCourse course = new NitriteMtmCourse(title: "History")
            NitriteMtmStudent student = new NitriteMtmStudent(name: "Charlie")
            student.courses = [course]
            course.students = [student]

        when:
            studentRepository.save(student)
            NitriteMtmStudent saved = studentRepository.findById(student.id).get()

            NitriteMtmCourse newCourse = new NitriteMtmCourse(title: "Geography")
            saved.courses.add(newCourse)
            studentRepository.update(saved)

            NitriteMtmStudent updated = studentRepository.findById(student.id).get()

        then:
            updated.courses.size() == 2
            updated.courses[0].title == "History"
            updated.courses[1].title == "Geography"
    }
}
