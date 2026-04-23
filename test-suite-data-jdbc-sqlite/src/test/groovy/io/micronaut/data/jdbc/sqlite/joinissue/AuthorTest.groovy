package io.micronaut.data.jdbc.sqlite.joinissue

import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@SQLiteDBProperties
class AuthorTest extends Specification {

    @Inject
    AuthorRepository authorRepository

    void test() {
        given:
            var authorList = List.of(
                    new Author(null, "Joe Doe",
                            Set.of(new Book(null, "History of nothing"))),
                    new Author(null, "Jane Doe",
                            Set.of(new Book(null, "History of everything"),
                                    new Book(null, "Doing awesome things"))))

            authorRepository.saveAll(authorList)

        when:
            Author author = authorRepository.queryByName("Joe Doe").orElse(null)
        then:
            author.name() == "Joe Doe"
            author.books().size() == 1

        when:
            List<Author> list = authorRepository.queryByNameContains("Doe")
        then:
            list.size() == 2
            list.get(0).name() == "Joe Doe"
            list.get(0).books().size() == 1
            list.get(1).name() == "Jane Doe"
            list.get(1).books().size() == 2

        when:
            author = authorRepository.getOneByNameContains("Doe").orElse(null)
        then:
            author.name() == "Joe Doe"
            author.books().size() == 1

        when:
            author = authorRepository.getOneByNameContains("ne Doe").orElse(null)
        then:
            author.name() == "Jane Doe"
            author.books().size() == 2

        when:
            author = this.authorRepository.findByNameContains("Doe").orElse(null)
        then:
            author.name() == "Joe Doe"
            author.books().size() == 1

        when:
            author = this.authorRepository.findByNameContains("e Doe").orElse(null)
        then:
            author.name() == "Joe Doe"
            author.books().size() == 1

        when:
            author = this.authorRepository.findByNameContains("ne Doe").orElse(null)
        then:
            author.name() == "Jane Doe"
            author.books().size() == 2
    }

}
