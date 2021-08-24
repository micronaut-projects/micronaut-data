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

import io.micronaut.data.tck.entities.EntityIdClass
import io.micronaut.data.tck.entities.EntityWithIdClass
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
    EntityWithIdClassRepository entityWithIdClassRepository

    @Shared
    @Inject
    JpaStudentRepository studentRepository

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



    void "author find by id with joins"() {
        when:
        def author = authorRepository.searchByName("Stephen King")
        author = authorRepository.findById(author.id).get()

        then:
        author.books.size() == 2
        author.books[0].pages.size() == 0
        author.books[1].pages.size() == 0
    }

    void "author find by id with EntityGraph"() {
        when:
        def author = authorRepository.searchByName("Stephen King")
        author = authorRepository.queryById(author.id).get()

        then:
        author.books.size() == 2
        author.books[0].pages.size() == 0
        author.books[1].pages.size() == 0
    }

    void "author dto"() {
        when:
        def authors = authorRepository.getAuthors()

        then:
        authors.size() == 3
        authors[0].authorId
        authors[0].authorName
        authors[1].authorId
        authors[1].authorName
        authors[2].authorId
        authors[2].authorName

        when:
        def author = authorRepository.getAuthorsById(authors[0].authorId)

        then:
        author
        author.authorId
        author.authorName
    }

    void "entity with id class"() {
        given:
        EntityWithIdClass e = new EntityWithIdClass()
        e.id1 = 11
        e.id2 = 22
        e.name = "Xyz"
        EntityIdClass k = new EntityIdClass()
        k.id1 = 11
        k.id2 = 22

        when:
        entityWithIdClassRepository.save(e)
        e = entityWithIdClassRepository.findById(k).get()

        then:
        e.id1 == 11
        e.id2 == 22
        e.name == "Xyz"

        when:
        e.name = "abc"
        entityWithIdClassRepository.update(e)
        e = entityWithIdClassRepository.findById(k).get()

        then:
        e.id1 == 11
        e.id2 == 22
        e.name == "abc"

        when:
        entityWithIdClassRepository.delete(e)
        def result = entityWithIdClassRepository.findById(k)

        then:
        !result.isPresent()
    }

    void "test @Where annotation placeholder"() {
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
            def books4 = bookRepository.listNativeBooksNullableListSearch(["The Stand", "FFF"])
        then:
            books4.size() == 1
        when:
            def books5 = bookRepository.listNativeBooksNullableListSearch(["Xyz", "FFF"])
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
            def books8 = bookRepository.listNativeBooksNullableArraySearch(new String[] {"Xyz", "Ffff", "zzz"})
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

    void "test IN queries"() {
        when:
            def books1 = bookRepository.listNativeBooksWithTitleInCollection(null)
        then:
            books1.size() == 0
        when:
            def books2 = bookRepository.listNativeBooksWithTitleInCollection(["The Stand", "Along Came a Spider", "FFF"])
        then:
            books2.size() == 2
        when:
            def books3 = bookRepository.listNativeBooksWithTitleInCollection([])
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listNativeBooksWithTitleInArray(null)
        then:
            books4.size() == 0
        when:
            def books5 = bookRepository.listNativeBooksWithTitleInArray(new String[] {"The Stand", "Along Came a Spider", "FFF"})
        then:
            books5.size() == 2
        when:
            def books6 = bookRepository.listNativeBooksWithTitleInArray(new String[0])
        then:
            books6.size() == 0
    }

    void "test IN queries with multiple parameters"() {
        when:
            def books1 = bookRepository.listNativeBooksNullableListSearchWithExtraParameter(["The Stand", "FFF"], true)
        then:
            books1.size() == 1
    }

    void "test join on many ended association"() {
        when:
        def author = authorRepository.searchByName("Stephen King")

        then:
        author != null
        author.books.size() == 2
    }

    void "test update many"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
            author.getBooks().forEach() { it.title = it.title + " updated" }
            bookRepository.updateBooks(author.getBooks())
            author = authorRepository.searchByName("Stephen King")
        then:
            author.getBooks().every {it.title.endsWith(" updated")}
    }

    void "test update custom only titles"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 2
        when:
            author.getBooks().forEach() {
                it.title = it.title + " updated"
                it.totalPages = -1
            }
            bookRepository.updateCustomOnlyTitles(author.getBooks())
            author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 2
            author.getBooks().every {it.totalPages > 0}
    }

    void "test custom insert"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 2
        when:
            bookRepository.saveCustom([new Book(title: "Abc", totalPages: 12, author: author), new Book(title: "Xyz", totalPages: 22, author: author)])
            def authorAfter = authorRepository.searchByName("Stephen King")
        then:
            authorAfter.books.size() == 4
            authorAfter.books.find { it.title == "Abc" }
            authorAfter.books.find { it.title == "Xyz" }
    }

    void "test custom single insert"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 2
        when:
            bookRepository.saveCustomSingle(new Book(title: "Abc", totalPages: 12, author: author))
            def authorAfter = authorRepository.searchByName("Stephen King")
        then:
            authorAfter.books.size() == 3
            authorAfter.books.find { it.title == "Abc" }
    }

    void "test custom update"() {
        when:
            def books = bookRepository.findAllByTitleStartsWith("Along Came a Spider")
        then:
            books.size() == 1
            bookRepository.findAllByTitleStartsWith("Xyz").isEmpty()
        when:
            bookRepository.updateNamesCustom("Xyz", "Along Came a Spider")
        then:
            bookRepository.findAllByTitleStartsWith("Along Came a Spider").isEmpty()
            bookRepository.findAllByTitleStartsWith("Xyz").size() == 1
    }

    void "test custom delete"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 2
        when:
            author.books.find {it.title == "The Stand"}.title = "DoNotDelete"
            def deleted = bookRepository.deleteCustom(author.books)
            author = authorRepository.searchByName("Stephen King")
        then:
            deleted == 1
            author.books.size() == 1
    }

    void "test custom delete single"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 2
        when:
            def book = author.books.find { it.title == "The Stand" }
            book.title = "DoNotDelete"
            def deleted = bookRepository.deleteCustomSingle(book)
            author = authorRepository.searchByName("Stephen King")
        then:
            deleted == 0
            author.books.size() == 2
        when:
            book = author.books.find { it.title == "The Stand" }
            deleted = bookRepository.deleteCustomSingle(book)
            author = authorRepository.searchByName("Stephen King")
        then:
            deleted == 1
            author.books.size() == 1
    }

    void "test custom delete by title"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 2
        when:
            bookRepository.deleteCustomByName("The Stand")
            author = authorRepository.searchByName("Stephen King")
        then:
            author.books.size() == 1
    }

    void "test update relation custom query"() {
        when:
            def book = bookRepository.findAllByTitleStartsWith("Along Came a Spider").first()
            def author = authorRepository.searchByName("Stephen King")
            bookRepository.updateAuthorCustomQuery(book.id, author)
            book = bookRepository.findById(book.id).get()
        then:
            book.author.id == book.author.id
    }

    void "test update relation"() {
        when:
            def book = bookRepository.findAllByTitleStartsWith("Along Came a Spider").first()
            def author = authorRepository.searchByName("Stephen King")
            bookRepository.updateAuthor(book.id, author)
            book = bookRepository.findById(book.id).get()
        then:
            book.author.id == book.author.id
    }

    void "test query by relation"() {
        when:
            def author = authorRepository.searchByName("Stephen King")
            def books = bookRepository.findByAuthor(author)
        then:
            books.size() == 2
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
