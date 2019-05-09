package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import javax.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class ProjectionSpec extends Specification {

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    @Inject
    @Shared
    AuthorRepository authorRepository

    @Inject
    @Shared
    BookRepository bookRepository

    def setupSpec() {
        crudRepository.saveAll([
                new Person(name: "Jeff", age: 40),
                new Person(name: "Ivan", age: 30),
                new Person(name: "James", age: 35)
        ])

        def king = new Author(name: "Stephen King")
        king.books.add(new Book(author: king, title: "The Stand", pages: 1000))
        king.books.add(new Book(author: king, title: "Pet Cemetery", pages: 400))

        def jp = new Author(name: "James Patterson")
        jp.books.add(new Book(author: jp, title: "Along Came a Spider", pages: 300 ))
        jp.books.add(new Book(author: jp, title: "Double Cross", pages: 300 ))
        def dw = new Author(name: "Don Winslow")
        dw.books.add(new Book(author: dw, title: "The Power of the Dog", pages: 600))
        dw.books.add(new Book(author: dw, title: "The Border", pages: 700))
        authorRepository.saveAll([
                king,
                jp,
                dw
        ])
    }

    void "test project on single property"() {
        expect:
        bookRepository.findTop3OrderByTitle()[0].title == 'Along Came a Spider'
        bookRepository.findTop3OrderByTitle().size() == 3
        crudRepository.findAgeByName("Jeff") == 40
        crudRepository.findAgeByName("Ivan") == 30
        crudRepository.findMaxAgeByNameLike("J%") == 40
        crudRepository.findMinAgeByNameLike("J%") == 35
        crudRepository.getSumAgeByNameLike("J%") == 75
        crudRepository.getAvgAgeByNameLike("J%") == 37
        crudRepository.readAgeByNameLike("J%").sort() == [35,40]
        crudRepository.findByNameLikeOrderByAge("J%")*.age == [35,40]
        crudRepository.findByNameLikeOrderByAgeDesc("J%")*.age == [40,35]
    }

    void "test project on single ended association"() {
        expect:
        bookRepository.count() == 6
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .findFirst().get().title == "Pet Cemetery"
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                      .count() == 2
        authorRepository.findByName("Stephen King").books.size() == 2
        authorRepository.findByBooksTitle("The Stand").name == "Stephen King"
        authorRepository.findByBooksTitle("The Border").name == "Don Winslow"
        bookRepository.findByAuthorName("Stephen King").size() == 2
    }
}
