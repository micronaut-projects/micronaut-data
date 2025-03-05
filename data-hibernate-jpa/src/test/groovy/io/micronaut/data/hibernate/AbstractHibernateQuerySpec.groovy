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

import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.hibernate.entities.Rating
import io.micronaut.data.hibernate.entities.RelPerson
import io.micronaut.data.hibernate.entities.UserWithWhere
import io.micronaut.data.jpa.repository.criteria.Specification
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.EntityIdClass
import io.micronaut.data.tck.entities.EntityWithIdClass
import io.micronaut.data.tck.entities.Order
import io.micronaut.data.tck.entities.Product
import io.micronaut.data.tck.entities.Student
import io.micronaut.data.tck.repositories.BookSpecifications
import io.micronaut.data.tck.tests.AbstractQuerySpec
import jakarta.inject.Inject
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import org.hibernate.LazyInitializationException
import spock.lang.Issue
import spock.lang.Shared

import jakarta.persistence.OptimisticLockException

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

    @Shared
    @Inject
    RatingRepository ratingRepository

    @Shared
    @Inject
    UserWithWhereRepository userWithWhereRepository

    @Shared
    @Inject
    ProductRepo productRepo

    @Shared
    @Inject
    RelPersonRepository relPersonRepo

    void "test where in empty list of entities"() {
        when:
        def found = bookRepository.findByAuthors(List.of())
        then:
        found.empty
        when:
        def author = authorRepository.findByName("Stephen King")
        found = bookRepository.findByAuthors(List.of(author))
        then:
        !found.empty
    }

    void "test where in empty list of basic type"() {
        when:
        def found = bookRepository.findByAuthorIds(List.of())
        then:
        found.empty
        when:
        def author = authorRepository.findByName("Stephen King")
        found = bookRepository.findByAuthorIds(List.of(author.id))
        then:
        !found.empty
    }

    void "test @where with nullable property values"() {
        when:
            userWithWhereRepository.update(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: null))
        then:
            noExceptionThrown()
    }

    void "test @where on find one"() {
        when:
            def e = userWithWhereRepository.save(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: false))
            def found = userWithWhereRepository.findById(e.id)
        then:
            found.isPresent()
        cleanup:
            userWithWhereRepository.deleteById(e.id)
    }

    void "test @where on find one deleted"() {
        when:
            def e = userWithWhereRepository.save(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: true))
            def found = userWithWhereRepository.findById(e.id)
        then:
            !found.isPresent()
    }

    void "test merge"() {
        given:
            studentRepository.deleteAll()
            def student = new Student("Denis")
            student = studentRepository.save(student)
        when:
            Student newStudent = studentRepository.findById(student.id).get()
        then:
            newStudent
        when:
            newStudent.setName("Denis 2")
            Student newStudent2 = studentRepository.merge(newStudent)
        then:
            newStudent2.name == "Denis 2"
        when:
            Student newStudent3 = studentRepository.findById(student.id).get()
        then:
            newStudent3.name == "Denis 2"
    }

    void "test optimistic locking"() {
        given:
            studentRepository.deleteAll()
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
            noExceptionThrown()
        when:
            studentRepository.delete(student3.getId(), student3.getVersion(), student3.getName())
            def student4 = studentRepository.findById(student2.getId())
        then:
            !student4.isPresent()
        cleanup:
            studentRepository.deleteAll()
    }

    void "order by joined collection"() {
        when:
            def books1 = bookRepository.listPageableCustomQuery(Pageable.from(0).order("author.name").order("title")).getContent()
            def books2 = bookRepository.listPageableCustomQuery2(Pageable.from(0).order("author.name").order("title")).getContent()
            def books3 = bookRepository.findAll(Pageable.from(0).order("author.name").order("title")).getContent()

        then:
            books1.size() == 6
            books2.size() == 6
            books3.size() == 6
            books1[0].title == "The Border"
            books2[0].title == "The Border"
            books3[0].title == "The Border"
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

    void "author find empty"() {
        when:
        def author = authorRepository.retrieveByName("XYZ")

        then:
        author == null

        when:
        authorRepository.searchByName("XYZ")

        then:
        thrown(EmptyResultException)
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

    void "Rating find by id with named EntityGraph"() {
        setup:
        Book book = bookRepository.findByTitle("The Power of the Dog")
        Author ratingAuthor = authorRepository.findByName("Stephen King")
        Rating rating = new Rating()
        rating.setRating(2)
        rating.setComment("wow, much book, so pages, wow")
        rating.setBook(book)
        rating.setAuthor(ratingAuthor)
        Rating savedRating = ratingRepository.save(rating)

        when: 'Testing method annotated with @EntityGraph referencing an existing @NamedEntityGraph'
        Rating namedEGraphRating = ratingRepository.findById(savedRating.id).orElse(null)

        then: 'All the paths in the EntityGraph are eagerly fetched and will not trigger a lazy loading'
        namedEGraphRating != null
        namedEGraphRating.book != null
        namedEGraphRating.book.pages.size() == 0
        namedEGraphRating.book.author != null
        namedEGraphRating.book.author.name == book.author.name
        namedEGraphRating.author != null
        namedEGraphRating.author.name == ratingAuthor.name

        when: 'Annotated with @EntityGraph with a list of attributeNames containing multiple paths on the book relation'
        Rating bookEGraphRating = ratingRepository.queryById(savedRating.id).orElse(null)

        then: 'All the paths specified path are eagerly fetched'
        bookEGraphRating != null
        bookEGraphRating.book != null
        bookEGraphRating.book.pages.size() == 0
        bookEGraphRating.book.author != null
        bookEGraphRating.book.author.name == book.author.name

        when: 'Trying to access a association that was not in the list of attributeNames'
        bookEGraphRating.book.author.books.size() == 2

        then: 'A lazy loading is triggered and fail outside a session'
        thrown(LazyInitializationException)

        when: 'Annotated with @EntityGraph with a list of attributeNames containing multiple relation paths'
        Rating relEGraphRating = ratingRepository.getById(savedRating.id).orElse(null)

        then: 'All the paths specified path are eagerly fetched'
        relEGraphRating != null
        relEGraphRating.book != null
        relEGraphRating.book.pages.size() == 0
        relEGraphRating.book.author != null
        relEGraphRating.book.author.name == book.author.name
        relEGraphRating.author != null
        relEGraphRating.author.name == ratingAuthor.name
        relEGraphRating.author.books.size() == 2

        cleanup:
        ratingRepository.deleteById(savedRating.id)
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

    void "author dto result from native query"() {
        when:
        def sort = Sort.of(Sort.Order.desc("authorName"))
        def authors = authorRepository.getAuthorsByNativeQuery(sort)

        then:
        authors
        authors.size() == 3
        authors[0].authorId
        authors[0].authorName
        authors[1].authorId
        authors[1].authorName
        authors[2].authorId
        authors[2].authorName
        // verify sorted desc by authorName
        authors[0].authorName >= authors[1].authorName
        authors[1].authorName >= authors[2].authorName
    }

    void "entity with id class"() {
        given:
        EntityWithIdClass e = new EntityWithIdClass()
        e.id1 = 11
        e.id2 = 22
        e.name = "Xyz"
        EntityWithIdClass f = new EntityWithIdClass()
        f.id1 = 33
        f.id2 = e.id2
        f.name = "Xyz"
        EntityWithIdClass g = new EntityWithIdClass()
        g.id1 = e.id1
        g.id2 = 44
        g.name = "Xyz"
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
        entityWithIdClassRepository.save(f)
        List<EntityWithIdClass> ef = entityWithIdClassRepository.findById2(e.id2)

        then:
        ef.size() == 2

        when:
        entityWithIdClassRepository.save(g)
        List<EntityWithIdClass> eg = entityWithIdClassRepository.findById1(e.id1)

        then:
        eg.size() == 2

        when:
        e.name = "abc"
        entityWithIdClassRepository.update(e)
        e = entityWithIdClassRepository.findById(k).get()

        then:
        e.id1 == 11
        e.id2 == 22
        e.name == "abc"

        when:
        def cnt = entityWithIdClassRepository.count()
        def cntDistinct = entityWithIdClassRepository.countDistinct()
        def cntDistinctName = entityWithIdClassRepository.countDistinctName()

        then:
        cnt == 3
        cntDistinct <= cnt
        cntDistinctName <= cntDistinctName

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
        when:
            def books7 = bookRepository.listNativeBooksWithTitleInCollection(Collections.singletonList("The Stand"))
            def books7a = bookRepository.listNativeBooksWithTitleInArray(new String[] {"The Stand"})
            def books8 = bookRepository.listNativeBooksWithTitleInCollection(Collections.singletonList("FFF"))
            def books8a = bookRepository.listNativeBooksWithTitleInArray(new String[] {"FFF"})
        then:
            books7.size() == 1
            books7a.size() == 1
            books8.size() == 0
            books8a.size() == 0
    }

    @Issue('https://github.com/micronaut-projects/micronaut-data/issues/1131')
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
        given:
            def book1 = new Book(title: "Abc", totalPages: 12)
            def book2 = new Book(title: "Xyz", totalPages: 22)

        when:
            def books = bookRepository.findAll()
        then:
            books.size() == 8

        when:
            // Hibernate doesn't support updating other tables
            bookRepository.saveCustom([book1, book2])
            def booksAfter = bookRepository.findAll()
        then:
            !book1.id // Ids cannot be updated by a custom query
            !book2.id
            booksAfter.size() == 10
            booksAfter.find { it.title == "Abc" }
            booksAfter.find { it.title == "Xyz" }
    }

    void "test custom single insert"() {
        given:
            def book = new Book(title: "Abc", totalPages: 12)
        when:
            def books = bookRepository.findAll()
        then:
            books.size() == 8
        when:
            // Hibernate doesn't support updating other tables
            bookRepository.saveCustomSingle(book)
            def booksAfter = bookRepository.findAll()
        then:
            !book.id // Ids cannot be updated by a custom query
            booksAfter.size() == 9
            booksAfter.find { it.title == "Abc" }
    }

    void "test custom single insert expressions"() {
        given:
            def book = new Book(title: "Abc", totalPages: 12)
        when:
            def books = bookRepository.findAll()
        then:
            books.size() == 8
        when:
            // Hibernate doesn't support updating other tables
            bookRepository.saveCustomSingleExpressions(book)
            def booksAfter = bookRepository.findAll()
        then:
            !book.id // Ids cannot be updated by a custom query
            booksAfter.size() == 9
            booksAfter.find { it.title == "AbcXYZ" }
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

    void "test limit with native query" () {
        when:
            def firstBook = bookRepository.findFirstBook();
        then:
            firstBook != null
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

    void "test native query with numbers"() {
        when:
            def value = authorRepository.longs()
        then:
            !value.isEmpty()
    }

    void "test specification and pageable"() {
        when:
            def value = bookRepository.findAll(testJoin("Stephen King"), Pageable.from(0));
        then:
            value.totalSize == 2
            value.content.size() == 2
        when:
            value = bookRepository.findAll((Specification<Book>)null, Pageable.from(0))
            def count = bookRepository.count((PredicateSpecification<Book>) null)
        then:
            count
            value.totalSize > 2
            value.totalSize == count
            value.content.size() > 2
            def optBook = value.content.stream().filter(b -> b.title == "The Stand").findFirst()
            optBook.present
        when:
            value = bookRepository.findAll(testJoin("Stephen King"), Pageable.from(0)
                    .order(new Sort.Order("author.name")).order(new Sort.Order("title")))
        then:
            value.totalSize == 2
            value.content.size() == 2
            value.content[0].title == "Pet Cemetery"

        when:
            def bookDtoPage = bookRepository.findByTotalPagesLessThan(1000, Pageable.from(0, 10, Sort.of(Sort.Order.asc("title"))))
        then:
            bookDtoPage.totalSize > 1
    }

    void "test loading entity with MappedProperty and alias"() {
        given:
            def product = new Product("prod1", 20)
            product.setLongName("prod name1")
            product = productRepo.save(product)
        when:
            def loadedProd = productRepo.findById(product.getId()).get()
        then:
            loadedProd != null
            loadedProd.id == product.id
            loadedProd.longName == product.longName
        cleanup:
          productRepo.deleteAll()
    }

    void "test relations person repository and joins"() {
        given:
            def parent = new RelPerson()
            parent.name = 'RelParent'
            relPersonRepo.save(parent)
            def child1Friend1 = new RelPerson()
            child1Friend1.name = 'Child1Friend1'
            relPersonRepo.save(child1Friend1)
            def child1Friend2 = new RelPerson()
            child1Friend2.name = 'Child1Friend2'
            relPersonRepo.save(child1Friend2)
            def child2Friend1 = new RelPerson()
            child2Friend1.name = 'Child2Friend1'
            relPersonRepo.save(child2Friend1)
            def child1 = new RelPerson()
            child1.name = 'Child1'
            child1.parent = parent
            child1.friends = [child1Friend1, child1Friend2]
            relPersonRepo.save(child1)
            def child2 = new RelPerson()
            child2.name = 'Child2'
            child2.parent = parent
            child2.friends = [child2Friend1]
            relPersonRepo.save(child2)
        when:
            def result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByParentAndFriends(parent.id, List.of(child1Friend1.id, child1Friend2.id)))
        then:
            result.size() == 1
            result[0].id == child1.id
        when:"findAll using null PredicateSpecification"
            result = relPersonRepo.findAll((PredicateSpecification<RelPerson>) null)
        then:
            result.size() > 1
        when:
            result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByParentAndFriends(parent.id, List.of(child1Friend1.id, child1Friend2.id, child2Friend1.id)))
        then:
            result.size() == 2
        when:
            result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByChildren(List.of(child1.id, child2.id)))
        then:
            result.size() == 1
            result[0].id == parent.id
        when:
            result = (List<RelPerson>) relPersonRepo.findAll(RelPersonRepository.Specifications.findRelPersonByChildren(List.of(child1Friend1.id, child1Friend2.id, child2Friend1.id)))
        then:
            result.size() == 0
    }

    void "test distinct count criteria"() {
        given:
            relPersonRepo.deleteAll()
            10.times {
                def person = new RelPerson()
                person.name = 'Person'
                relPersonRepo.save(person)
            }
        when:
            def result = relPersonRepo.findOne(RelPersonRepository.Specifications.countDistinct())
        then:
            result == 10
    }

    void "test count criteria "() {
        given:
            relPersonRepo.deleteAll()
            10.times {
                def person = new RelPerson()
                person.name = 'Person'
                relPersonRepo.save(person)
            }
        when:
            def result = relPersonRepo.findOne(RelPersonRepository.Specifications.countDistinct())
        then:
            result == 10
    }

    void "test order by embedded field"() {
        when:
            def e1 = userWithWhereRepository.save(new UserWithWhere(id: UUID.randomUUID(), email: "where1@somewhere.com", deleted: false))
            def u2 = new UserWithWhere(id: UUID.randomUUID(), email: "where2@somewhere.com", deleted: false)
            u2.audit.createdTime = u2.audit.createdTime.plusSeconds(30)
            def e2 = userWithWhereRepository.save(u2)
            def found1 = userWithWhereRepository.findById(e1.id)
            def found2 = userWithWhereRepository.findById(e2.id)
        then:
            found1.present
            found2.present
        when:"Sorted by embedded field works"
            def sortedItems = userWithWhereRepository.findAllByIdInList(List.of(e1.id, e2.id), Sort.of(Sort.Order.desc("audit.createdTime", false)))
        then:
            sortedItems
            sortedItems.size() == 2
            sortedItems[0].id == e2.id
            sortedItems[1].id == e1.id
        cleanup:
            userWithWhereRepository.deleteAll(List.of(e1, e2))
    }

    void "test findBy and count with multiple parameters"() {
        when:
        def bookTitles = List.of("The Stand", "Pet Cemetery")
        def books = bookRepository.findByTitleInAndTotalPagesGreaterThan(bookTitles, 1)
        def cnt = bookRepository.countByTitleInAndTotalPagesGreaterThan(bookTitles, 1)
        then:
        books.size() == 2
        cnt == 2
    }

    void "test subquery criteria"() {
        when:
            def book = bookRepository.findOne(BookSpecifications.findUsingASubquery("The Stand"))
        then:
            book.title == "The Stand"
    }

    private static Specification<Book> testJoin(String value) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.join("author").get("name"), value))
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
