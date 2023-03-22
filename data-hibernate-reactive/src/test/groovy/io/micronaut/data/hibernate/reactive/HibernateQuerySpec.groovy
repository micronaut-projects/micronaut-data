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
package io.micronaut.data.hibernate.reactive

import io.micronaut.data.hibernate.reactive.entities.Rating
import io.micronaut.data.hibernate.reactive.entities.UserWithWhere
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.AuthorBooksDto
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.EntityIdClass
import io.micronaut.data.tck.entities.EntityWithIdClass
import io.micronaut.data.tck.entities.Student
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.hibernate.LazyInitializationException
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.OptimisticLockException

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
// TODO: Re-enable when possible
@Ignore("Temp disabled failing test")
class HibernateQuerySpec extends Specification implements PostgresHibernateReactiveProperties {

    @Shared
    @Inject
    BookRepository bookRepository

    @Shared
    @Inject
    AuthorRepository authorRepository

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

    void setup() {
        addBookSeedData()
    }

    void cleanup() {
        bookRepository.deleteAll().block()
        authorRepository.deleteAll().block()
    }

    void addBookSeedData() {
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400)).block()
        // blank title
        bookRepository.save(new Book(title: "", totalPages: 0)).block()
        // book without an author
        saveSampleBooks()
    }

    void saveSampleBooks() {
        bookRepository.saveAuthorBooks(authorRepository, [
                new AuthorBooksDto("Stephen King", Arrays.asList(
                        new BookDto("The Stand", 1000),
                        new BookDto("Pet Cemetery", 400)
                )),
                new AuthorBooksDto("James Patterson", Arrays.asList(
                        new BookDto("Along Came a Spider", 300),
                        new BookDto("Double Cross", 300)
                )),
                new AuthorBooksDto("Don Winslow", Arrays.asList(
                        new BookDto("The Power of the Dog", 600),
                        new BookDto("The Border", 700)
                ))]).block()
    }

    void "test @where with nullable property values"() {
        when:
            userWithWhereRepository.update(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: null)).block()
        then:
            noExceptionThrown()
    }

    void "test @where on find one"() {
        when:
            def e = userWithWhereRepository.save(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: false)).block()
            def found = userWithWhereRepository.findById(e.id).block()
        then:
            found
    }

    void "test @where on find one deleted"() {
        when:
            def e = userWithWhereRepository.save(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: true)).block()
            def found = userWithWhereRepository.findById(e.id).block()
        then:
            !found
    }

    void "test optimistic locking"() {
        when:
            studentRepository.testUpdateOptimisticLock(new Student("Denis")).block()
        then:
            thrown(OptimisticLockException)
        when:
            studentRepository.testDeleteOptimisticLock(new Student("Denis")).block()
        then:
            thrown(OptimisticLockException)
        when:
            studentRepository.testMergeOptimisticLock(new Student("Denis")).block()
        then:
            thrown(OptimisticLockException)
    }

    void "order by joined collection"() {
        when:
            def books3 = bookRepository.findAll(Pageable.from(0).order("author.name").order("title")).block().getContent()

        then:
            books3.size() == 6
            books3[0].title == "The Border"
    }

    void "author find by id with joins"() {
        when:
        def author = authorRepository.searchByName("Stephen King").block()
        author = authorRepository.findById(author.id).block()

        then:
        author.books.size() == 2
        author.books[0].pages.size() == 0
        author.books[1].pages.size() == 0
    }

    void "author find empty"() {
        when:
        def author = authorRepository.findByName("XYZ").block()

        then:
        author == null
    }

    void "author find by id with EntityGraph"() {
        when:
        def author = authorRepository.searchByName("Stephen King").block()
        author = authorRepository.queryById(author.id).block()

        then:
        author.books.size() == 2
        author.books[0].pages.size() == 0
        author.books[1].pages.size() == 0
    }

    void "Rating find by id with named EntityGraph"() {
        setup:
        Book book = bookRepository.findByTitle("The Power of the Dog").block()
        Author ratingAuthor = authorRepository.findByName("Stephen King").block()
        Rating rating = new Rating()
        rating.setRating(2)
        rating.setComment("wow, much book, so pages, wow")
        rating.setBook(book)
        rating.setAuthor(ratingAuthor)
        Rating savedRating = ratingRepository.save(rating).block()

        when: 'Testing method annotated with @EntityGraph referencing an existing @NamedEntityGraph'
        Rating namedEGraphRating = ratingRepository.findById(savedRating.id).block()

        then: 'All the paths in the EntityGraph are eagerly fetched and will not trigger a lazy loading'
        namedEGraphRating != null
        namedEGraphRating.book != null
        namedEGraphRating.book.pages.size() == 0
        namedEGraphRating.book.author != null
        namedEGraphRating.book.author.name == book.author.name
        namedEGraphRating.author != null
        namedEGraphRating.author.name == ratingAuthor.name

        when: 'Annotated with @EntityGraph with a list of attributeNames containing multiple paths on the book relation'
        Rating bookEGraphRating = ratingRepository.queryById(savedRating.id).block()

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
        Rating relEGraphRating = ratingRepository.getById(savedRating.id).block()

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
        ratingRepository.deleteById(savedRating.id).block()
    }

    void "author dto"() {
        when:
        def authors = authorRepository.getAuthors().collectList().block()

        then:
        authors.size() == 3
        authors[0].authorId
        authors[0].authorName
        authors[1].authorId
        authors[1].authorName
        authors[2].authorId
        authors[2].authorName

        when:
        def author = authorRepository.getAuthorsById(authors[0].authorId).block()

        then:
        author
        author.authorId
        author.authorName
    }

    void "author dto result from native query"() {
        when:
        def author = authorRepository.getAuthorsByNativeQuery().collectList().block()

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
        entityWithIdClassRepository.save(e).block()
        e = entityWithIdClassRepository.findById(k).block()

        then:
        e.id1 == 11
        e.id2 == 22
        e.name == "Xyz"

        when:
        entityWithIdClassRepository.save(f).block()
        List<EntityWithIdClass> ef = entityWithIdClassRepository.findById2(e.id2).collectList().block()

        then:
        ef.size() == 2

        when:
        entityWithIdClassRepository.save(g).block()
        List<EntityWithIdClass> eg = entityWithIdClassRepository.findById1(e.id1).collectList().block()

        then:
        eg.size() == 2

        when:
        e.name = "abc"
        entityWithIdClassRepository.update(e).block()
        e = entityWithIdClassRepository.findById(k).block()

        then:
        e.id1 == 11
        e.id2 == 22
        e.name == "abc"

        when:
        entityWithIdClassRepository.findByIdAndDelete(k).block()
        def result = entityWithIdClassRepository.findById(k).blockOptional()

        then:
        !result.isPresent()
    }

    void "test @Where annotation placeholder"() {
        given:
        def size = bookRepository.countNativeByTitleWithPagesGreaterThan("The%", 300).block()
        def books = bookRepository.findByTitleStartsWith("The", 300).collectList().block()

        expect:
        books.size() == size
    }

    void "test native query"() {
        given:
        def books = bookRepository.listNativeBooks("The%").collectList().block()

        expect:
        books.size() == 3
        books.every({ it instanceof Book })
    }

    @PendingFeature(reason = "setParameterList method is missing")
    void "test native query with nullable property"() {
        when:
            def books1 = bookRepository.listNativeBooksNullableSearch(null).collectList().block()
        then:
            books1.size() == 8
        when:
            def books2 = bookRepository.listNativeBooksNullableSearch("The Stand").collectList().block()
        then:
            books2.size() == 1
        when:
            def books3 = bookRepository.listNativeBooksNullableSearch("Xyz").collectList().block()
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listNativeBooksNullableListSearch(["The Stand", "FFF"]).collectList().block()
        then:
            books4.size() == 1
        when:
            def books5 = bookRepository.listNativeBooksNullableListSearch(["Xyz", "FFF"]).collectList().block()
        then:
            books5.size() == 0
        when:
            def books6 = bookRepository.listNativeBooksNullableListSearch([]).collectList().block()
        then:
            books6.size() == 0
        when:
            def books7 = bookRepository.listNativeBooksNullableListSearch(null).collectList().block()
        then:
            books7.size() == 0
        when:
            def books8 = bookRepository.listNativeBooksNullableArraySearch(new String[] {"Xyz", "Ffff", "zzz"}).collectList().block()
        then:
            books8.size() == 0
        when:
            def books9 = bookRepository.listNativeBooksNullableArraySearch(new String[] {}).collectList().block()
        then:
            books9.size() == 0
        when:
            def books11 = bookRepository.listNativeBooksNullableArraySearch(null).collectList().block()
        then:
            books11.size() == 0
        then:
            def books12 = bookRepository.listNativeBooksNullableArraySearch(new String[] {"The Stand"}).collectList().block()
        then:
            books12.size() == 1
    }

    void "test IN queries"() {
        when:
            def books1 = bookRepository.listNativeBooksWithTitleInCollection(null).collectList().block()
        then:
            books1.size() == 0
        when:
            def books2 = bookRepository.listNativeBooksWithTitleInCollection(["The Stand", "Along Came a Spider", "FFF"]).collectList().block()
        then:
            books2.size() == 2
        when:
            def books3 = bookRepository.listNativeBooksWithTitleInCollection([]).collectList().block()
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listNativeBooksWithTitleInArray(null).collectList().block()
        then:
            books4.size() == 0
        when:
            def books5 = bookRepository.listNativeBooksWithTitleInArray(new String[] {"The Stand", "Along Came a Spider", "FFF"}).collectList().block()
        then:
            books5.size() == 2
        when:
            def books6 = bookRepository.listNativeBooksWithTitleInArray(new String[0]).collectList().block()
        then:
            books6.size() == 0
        when:
            def books7 = bookRepository.listNativeBooksWithTitleInCollection(Collections.singletonList("The Stand")).collectList().block()
            def books7a = bookRepository.listNativeBooksWithTitleInArray(new String[] {"The Stand"}).collectList().block()
            def books8 = bookRepository.listNativeBooksWithTitleInCollection(Collections.singletonList("FFF")).collectList().block()
            def books8a = bookRepository.listNativeBooksWithTitleInArray(new String[] {"FFF"}).collectList().block()
        then:
            books7.size() == 1
            books7a.size() == 1
            books8.size() == 0
            books8a.size() == 0
    }

    @PendingFeature(reason = "setParameterList method is missing")
    @Issue('https://github.com/micronaut-projects/micronaut-data/issues/1131')
    void "test IN queries with multiple parameters"() {
        when:
            def books1 = bookRepository.listNativeBooksNullableListSearchWithExtraParameter(["The Stand", "FFF"], true).collectList().block()
        then:
            books1.size() == 1
    }

    void "test join on many ended association"() {
        when:
        def author = authorRepository.searchByName("Stephen King").block()

        then:
        author != null
        author.books.size() == 2
    }

    void "test update many"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
            author.getBooks().forEach() { it.title = it.title + " updated" }
            bookRepository.updateBooks(author.getBooks()).collectList().block()
            author = authorRepository.searchByName("Stephen King").block()
        then:
            author.getBooks().every {it.title.endsWith(" updated")}
    }

    void "test update custom only titles"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 2
        when:
            author.getBooks().forEach() {
                it.title = it.title + " updated"
                it.totalPages = -1
            }
            bookRepository.updateCustomOnlyTitles(author.getBooks()).block()
            author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 2
            author.getBooks().every {it.totalPages > 0}
    }

    void "test custom insert"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 2
        when:
            bookRepository.saveCustom([new Book(title: "Abc", totalPages: 12, author: author), new Book(title: "Xyz", totalPages: 22, author: author)]).block()
            def authorAfter = authorRepository.searchByName("Stephen King").block()
        then:
            authorAfter.books.size() == 4
            authorAfter.books.find { it.title == "Abc" }
            authorAfter.books.find { it.title == "Xyz" }
    }

    void "test custom single insert"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 2
        when:
            bookRepository.saveCustomSingle(new Book(title: "Abc", totalPages: 12, author: author)).block()
            def authorAfter = authorRepository.searchByName("Stephen King").block()
        then:
            authorAfter.books.size() == 3
            authorAfter.books.find { it.title == "Abc" }
    }

    void "test custom update"() {
        when:
            def books = bookRepository.findAllByTitleStartsWith("Along Came a Spider").collectList().block()
        then:
            books.size() == 1
            bookRepository.findAllByTitleStartsWith("Xyz").collectList().block().isEmpty()
        when:
            bookRepository.updateNamesCustom("Xyz", "Along Came a Spider").block()
        then:
            bookRepository.findAllByTitleStartsWith("Along Came a Spider").collectList().block().isEmpty()
            bookRepository.findAllByTitleStartsWith("Xyz").collectList().block().size() == 1
    }

    void "test limit with native query"() {
        when:
            def firstBook = bookRepository.findFirstBook().block()
        then:
            firstBook != null
    }

    void "test custom delete"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 2
        when:
            author.books.find {it.title == "The Stand"}.title = "DoNotDelete"
            def deleted = bookRepository.deleteCustom(author.books).block()
            author = authorRepository.searchByName("Stephen King").block()
        then:
            deleted == 1
            author.books.size() == 1
    }

    void "test custom delete single"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 2
        when:
            def book = author.books.find { it.title == "The Stand" }
            book.title = "DoNotDelete"
            def deleted = bookRepository.deleteCustomSingle(book).block()
            author = authorRepository.searchByName("Stephen King").block()
        then:
            deleted == 0
            author.books.size() == 2
        when:
            book = author.books.find { it.title == "The Stand" }
            deleted = bookRepository.deleteCustomSingle(book).block()
            author = authorRepository.searchByName("Stephen King").block()
        then:
            deleted == 1
            author.books.size() == 1
    }

    void "test custom delete by title"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 2
        when:
            bookRepository.deleteCustomByName("The Stand").block()
            author = authorRepository.searchByName("Stephen King").block()
        then:
            author.books.size() == 1
    }

    void "test update relation custom query"() {
        when:
            def book = bookRepository.findAllByTitleStartsWith("Along Came a Spider").collectList().block().first()
            def author = authorRepository.searchByName("Stephen King").block()
            bookRepository.updateAuthorCustomQuery(book.id, author)
            book = bookRepository.findById(book.id).block()
        then:
            book.author.id == book.author.id
    }

    void "test update relation"() {
        when:
            def book = bookRepository.findAllByTitleStartsWith("Along Came a Spider").collectList().block().first()
            def author = authorRepository.searchByName("Stephen King").block()
            bookRepository.updateAuthor(book.id, author).block()
            book = bookRepository.findById(book.id).block()
        then:
            book.author.id == book.author.id
    }

    void "test query by relation"() {
        when:
            def author = authorRepository.searchByName("Stephen King").block()
            def books = bookRepository.findByAuthor(author).collectList().block()
        then:
            books.size() == 2
    }

    void "test native query with numbers"() {
        when:
            def value = authorRepository.longs().collectList().block()
        then:
            !value.isEmpty()
    }

    void "test specification and pageable"() {
        when:
            def value = bookRepository.findAll(testJoin("Stephen King"), Pageable.from(0)).block();
        then:
            value.totalSize == 2
            value.content.size() == 2
        when:
            value = bookRepository.findAll(testJoin("Stephen King"), Pageable.from(0)
                    .order(new Sort.Order("author.name")).order(new Sort.Order("title"))).block()
        then:
            value.totalSize == 2
            value.content.size() == 2
            value.content[0].title == "Pet Cemetery"
    }

    private static io.micronaut.data.jpa.repository.criteria.Specification<Book> testJoin(String value) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.join("author").get("name"), value))
    }

}
