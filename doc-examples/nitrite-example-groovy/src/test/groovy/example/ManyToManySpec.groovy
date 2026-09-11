package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class ManyToManySpec extends Specification {

    @Inject BookRepository bookRepository
    @Inject StudentRepository studentRepository

    def cleanup() {
        bookRepository.deleteAll()
        studentRepository.deleteAll()
    }

    def "many-to-many save preserves student counts"() {
        given:
        Student student1 = new Student("Peter")
        Student student2 = new Student("Ivone")
        studentRepository.saveAll([student1, student2])

        Book book1 = new Book("The Roman Triumph")
        book1.getStudents().add(student2)

        Book book2 = new Book("Pompeii")
        book2.getStudents().add(student1)
        book2.getStudents().add(student2)

        when:
        bookRepository.saveAll([book1, book2])

        then:
        bookRepository.findById(book1.id).get().students.size() == 1
        bookRepository.findById(book2.id).get().students.size() == 2
    }

    def "find student by name"() {
        given:
        Student student1 = new Student("Peter")
        Student student2 = new Student("Ivone")
        studentRepository.saveAll([student1, student2])

        Book book = new Book("Pompeii")
        book.getStudents().add(student1)
        book.getStudents().add(student2)
        bookRepository.save(book)

        when:
        def saved = studentRepository.findByName("Peter")

        then:
        saved != null
        saved.name == "Peter"
    }
}
