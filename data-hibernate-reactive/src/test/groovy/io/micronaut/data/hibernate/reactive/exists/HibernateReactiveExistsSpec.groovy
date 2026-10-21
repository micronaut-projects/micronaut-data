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
package io.micronaut.data.hibernate.reactive.exists

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.data.hibernate.reactive.PostgresHibernateReactiveProperties
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.JoinType
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.hibernate.reactive.exists", transactional = false)
class HibernateReactiveExistsSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    @Shared
    ExistsAuthorRepository repository

    @Shared
    Logger sqlLogger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL")

    @Shared
    Level previousSqlLogLevel

    @Shared
    ListAppender<ILoggingEvent> sqlLogAppender = new ListAppender<>()

    def setupSpec() {
        repository.save(new ExistsAuthor("Stephen").addBook("The Stand").addBook("It").addBook("Carrie")).block()
        repository.save(new ExistsAuthor("Unpublished")).block()
        previousSqlLogLevel = sqlLogger.level
        sqlLogger.level = Level.DEBUG
        sqlLogAppender.start()
        sqlLogger.addAppender(sqlLogAppender)
    }

    def cleanupSpec() {
        sqlLogger.detachAppender(sqlLogAppender)
        sqlLogger.level = previousSqlLogLevel
    }

    def setup() {
        sqlLogAppender.list.clear()
    }

    void "an existence query with a collection fetch join doesn't read the entities"() {
        when:
            def exists = repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.LEFT)
                cb.equal(root.get("name"), "Stephen")
            } as QuerySpecification<ExistsAuthor>).block()

        then:
            exists
        and: "the books are not read"
            statements().size() == 1
            !statements()[0].contains("title")
        and: "the database only checks whether a row exists"
            statements()[0].contains("exists(")
    }

    void "the joins of the existence query keep their type"() {
        expect: "an inner fetch join excludes the roots without a joined row"
            !repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.INNER)
                cb.equal(root.get("name"), "Unpublished")
            } as QuerySpecification<ExistsAuthor>).block()
        and: "a left fetch join keeps them"
            repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.LEFT)
                cb.equal(root.get("name"), "Unpublished")
            } as QuerySpecification<ExistsAuthor>).block()
        and: "a predicate on a joined collection"
            repository.exists({ root, query, cb ->
                cb.equal(root.join("books").get("title"), "It")
            } as QuerySpecification<ExistsAuthor>).block()
            !repository.exists({ root, query, cb ->
                cb.equal(root.join("books").get("title"), "Misery")
            } as QuerySpecification<ExistsAuthor>).block()
    }

    void "a derived existence query reads at most one row"() {
        when:
            def exists = repository.existsByName("Stephen").block()

        then:
            exists
            statements().size() == 1
            statements()[0].contains("fetch first") || statements()[0].contains("limit")
        and:
            !repository.existsByName("Nobody").block()
    }

    private List<String> statements() {
        return sqlLogAppender.list.collect { it.formattedMessage.toLowerCase(Locale.ENGLISH) }
    }
}
