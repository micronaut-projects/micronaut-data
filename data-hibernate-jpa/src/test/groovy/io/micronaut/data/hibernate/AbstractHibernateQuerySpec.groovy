/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.hibernate

import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Student
import io.micronaut.data.tck.tests.AbstractQuerySpec
import spock.lang.Shared

import javax.inject.Inject
import javax.persistence.OptimisticLockException

abstract class AbstractHibernateQuerySpec extends AbstractQuerySpec {

    @Shared
    @Inject
    BookRepository br

    @Shared
    @Inject
    AuthorRepository ar

    @Shared
    @Inject
    JpaStudentRepository studentRepository

    void "test @Where annotation placehoder"() {
        given:
        def size = bookRepository.countNativeByTitleWithPagesGreaterThan("The%", 300)
        def books = bookRepository.findByTitleStartsWith("The", 300)

        expect:
        books.size() == size
    }

    void "test native query"() {
        given:
        def books = bookRepository.listNativeBooks("The%")

        expect:
        books.size() == 3
        books.every({ it instanceof Book })
    }

    void "test native query with nullable property"() {
        when:
        def books1 = bookRepository.listNativeBooksNullableSearch(null)

        then:
        books1.size() == 8

        when:
        def books2 = bookRepository.listNativeBooksNullableSearch("The Stand")

        then:
        books2.size() == 1

        when:
        def books3 = bookRepository.listNativeBooksNullableSearch("Xyz")

        then:
        books3.size() == 0

        when:
        def books4 = bookRepository.listNativeBooksNullableListSearch(["The Stand"])

        then:
        books4.size() == 1

        when:
        def books5 = bookRepository.listNativeBooksNullableListSearch(["Xyz"])

        then:
        books5.size() == 0

        when:
        def books6 = bookRepository.listNativeBooksNullableListSearch([])

        then:
        books6.size() == 0

        when:
        def books7 = bookRepository.listNativeBooksNullableListSearch(null)

        then:
        books7.size() == 0

        when:
        def books8 = bookRepository.listNativeBooksNullableArraySearch(new String[] {"Xyz"})

        then:
        books8.size() == 0

        when:
        def books9 = bookRepository.listNativeBooksNullableArraySearch(new String[] {})

        then:
        books9.size() == 0

        when:
        def books11 = bookRepository.listNativeBooksNullableArraySearch(null)

        then:
        books11.size() == 0

        then:
        def books12 = bookRepository.listNativeBooksNullableArraySearch(new String[] {"The Stand"})

        then:
        books12.size() == 1
    }

    void "test join on many ended association"() {
        when:
        def author = authorRepository.searchByName("Stephen King")

        then:
        author != null
        author.books.size() == 2
    }

    void "test optimistic locking"() {
        given:
            def student = new Student("Denis")
        when:
            studentRepository.save(student)
        then:
            student.version == 0
        when:
            student.setVersion(5)
            student.setName("Xyz")
            studentRepository.update(student)
        then:
            thrown(OptimisticLockException)
// Unable to establish parameter value for parameter at position: 1
//        when:
//            studentRepository.updateById(student.getId(), student.getVersion(), student.getName())
//        then:
//            thrown(OptimisticLockException)
//        when:
//            studentRepository.updateStudentName(student.getId(), student.getVersion(), student.getName())
//        then:
//            thrown(OptimisticLockException)
        when:
            studentRepository.delete(student)
        then:
            thrown(OptimisticLockException)
//        when:
//            studentRepository.delete(student.getId(), student.getVersion(), student.getName())
//        then:
//            thrown(OptimisticLockException)
        when:
            studentRepository.deleteAll([student])
        then:
            thrown(OptimisticLockException)
        when:
            student = studentRepository.findById(student.getId()).get()
        then:
            student.name == "Denis"
            student.version == 0
        when:
            student.setName("Abc")
            studentRepository.update(student)
            def student2 = studentRepository.findById(student.getId()).get()
        then:
            student.version == 0 // Hibernate doesn't update the entity instance
            student2.name == "Abc"
            student2.version == 1
        when:
            studentRepository.updateStudentName(student2.getId(), "Joe")
            def student3 = studentRepository.findById(student2.getId()).get()
        then:
            student3.name == "Joe"
            student3.version == 1
        when:
            studentRepository.updateStudentName(student2.getId(), student2.getVersion(), "Joe2")
            student3 = studentRepository.findById(student2.getId()).get()
        then:
            thrown(IllegalStateException) // TODO: Update version for Hibernate
        when:
            studentRepository.delete(student3.getId(), student3.getVersion(), student3.getName())
            def student4 = studentRepository.findById(student2.getId())
        then:
            !student4.isPresent()
        cleanup:
            studentRepository.deleteAll()
    }

    @Override
    BookRepository getBookRepository() {
        return br
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return ar
    }
}
