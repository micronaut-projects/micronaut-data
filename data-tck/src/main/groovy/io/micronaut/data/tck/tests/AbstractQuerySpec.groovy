package io.micronaut.data.tck.tests

import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookRepository
import spock.lang.Specification

abstract class AbstractQuerySpec extends Specification {
    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()

    void init() {
    }

    def setupSpec() {
        init()
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400))
        // blank title
        bookRepository.save(new Book(title: "", totalPages: 0))
        // book without an author
        bookRepository.setupData()
    }

    void "test is null or empty"() {
        expect:
        bookRepository.count() == 8
        bookRepository.findByAuthorIsNull().size() == 2
        bookRepository.findByAuthorIsNotNull().size() == 6
        bookRepository.countByTitleIsEmpty() == 1
        bookRepository.countByTitleIsNotEmpty() == 7
    }


    void "test string comparison methods"() {
        expect:
        authorRepository.countByNameContains("e") == 2
        authorRepository.findByNameStartsWith("S").name == "Stephen King"
        authorRepository.findByNameEndsWith("w").name == "Don Winslow"
        authorRepository.findByNameIgnoreCase("don winslow").name == "Don Winslow"
    }
}
