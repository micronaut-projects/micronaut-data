/*
 * Copyright 2017-2026 original authors
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
