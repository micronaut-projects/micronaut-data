package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.AuthorBooksDto
import io.micronaut.data.tck.repositories.*
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractJoinFetchSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    boolean leftJoinSupported = true

    @Shared
    boolean leftFetchJoinSupported = true

    @Shared
    boolean rightJoinSupported = true

    @Shared
    boolean rightFetchJoinSupported = true

    @Shared
    boolean outerJoinSupported = true

    @Shared
    boolean outerFetchJoinSupported = true

    @Shared
    boolean fetchJoinSupported = true

    @Shared
    boolean innerJoinSupported = true

    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinLeftFetchRepository getAuthorJoinLeftFetchRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinLeftRepository getAuthorJoinLeftRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinRightFetchRepository getAuthorJoinRightFetchRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinRightRepository getAuthorJoinRightRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinOuterRepository getAuthorJoinOuterRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinOuterFetchRepository getAuthorJoinOuterFetchRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinFetchRepository getAuthorJoinFetchRepository()

    abstract AuthorJoinTypeRepositories.AuthorJoinInnerRepository getAuthorJoinInnerRepository()

    void setup() {
        saveSampleBooks()
    }

    void cleanup() {
        bookRepository?.deleteAll()
        authorRepository?.deleteAll()
    }

    void saveSampleBooks() {
        bookRepository.saveAuthorBooks([
                new AuthorBooksDto("Stephen King", Arrays.asList(
                        new io.micronaut.data.tck.entities.BookDto("The Stand", 1000),
                        new io.micronaut.data.tck.entities.BookDto("Pet Sematary", 400)
                ))
        ])
    }

    @Requires({shared.leftJoinSupported})
    void "left join does not fetch projected entities"() {
        given:
        def authors = getAuthorJoinLeftRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.isEmpty()
    }

    @Requires({shared.leftFetchJoinSupported})
    void "left fetch join fetches projected entities"() {
        given:
        def authors = getAuthorJoinLeftFetchRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.title.containsAll(["The Stand", "Pet Sematary"])
    }

    @Requires({shared.rightJoinSupported})
    void "right join does not fetch projected entities"() {
        given:
        def authors = getAuthorJoinRightRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.isEmpty()
    }

    @Requires({shared.rightFetchJoinSupported})
    void "right fetch join fetches projected entities"() {
        given:
        def authors = getAuthorJoinRightFetchRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.title.containsAll(["The Stand", "Pet Sematary"])
    }

    @Requires({shared.outerJoinSupported})
    void "outer join does not fetch projected entities"() {
        given:
        def authors = getAuthorJoinOuterRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.isEmpty()
    }

    @Requires({shared.outerFetchJoinSupported})
    void "outer fetch join fetches projected entities"() {
        given:
        def authors = getAuthorJoinOuterFetchRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.title.containsAll(["The Stand", "Pet Sematary"])
    }

    @Requires({shared.fetchJoinSupported})
    void "fetch join fetches projected entities"() {
        given:
        def authors = getAuthorJoinFetchRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.title.containsAll(["The Stand", "Pet Sematary"])
    }

    @Requires({shared.innerJoinSupported})
    void "inner join does not fetch projected entities"() {
        given:
        def authors = getAuthorJoinInnerRepository().findAll()

        expect:
        !authors.isEmpty()
        authors.get(0).books.isEmpty()
    }


}
