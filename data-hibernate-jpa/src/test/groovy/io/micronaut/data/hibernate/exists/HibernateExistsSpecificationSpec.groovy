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
package io.micronaut.data.hibernate.exists

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.H2DBProperties
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.JoinType
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.hibernate.exists", transactional = false)
@H2DBProperties
@Property(name = "jpa.default.properties.hibernate.session_factory.statement_inspector", value = "io.micronaut.data.hibernate.exists.RecordingStatementInspector")
class HibernateExistsSpecificationSpec extends Specification {

    @Inject
    @Shared
    ExistsAuthorRepository repository

    def setupSpec() {
        repository.save(new ExistsAuthor("Stephen").addBook("The Stand").addBook("It").addBook("Carrie"))
        repository.save(new ExistsAuthor("Unpublished"))
    }

    def setup() {
        RecordingStatementInspector.STATEMENTS.clear()
    }

    void "an existence query with a collection fetch join doesn't read the entities"() {
        when:
            def exists = repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.LEFT)
                cb.equal(root.get("name"), "Stephen")
            } as QuerySpecification<ExistsAuthor>)

        then:
            exists
        and: "the books are not read"
            RecordingStatementInspector.STATEMENTS.size() == 1
            def sql = RecordingStatementInspector.STATEMENTS[0].toLowerCase(Locale.ENGLISH)
            !sql.contains("title")
        and: "the database only checks whether a row exists"
            sql.contains("exists(")
    }

    void "an inner fetch join still excludes the roots without a joined row"() {
        expect:
            !repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.INNER)
                cb.equal(root.get("name"), "Unpublished")
            } as QuerySpecification<ExistsAuthor>)
        and:
            repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.INNER)
                cb.equal(root.get("name"), "Stephen")
            } as QuerySpecification<ExistsAuthor>)
    }

    void "a left fetch join keeps the roots without a joined row"() {
        expect:
            repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.LEFT)
                cb.equal(root.get("name"), "Unpublished")
            } as QuerySpecification<ExistsAuthor>)
    }

    void "a predicate on a joined collection"() {
        expect:
            repository.exists({ root, query, cb ->
                cb.equal(root.join("books").get("title"), "It")
            } as QuerySpecification<ExistsAuthor>)
            !repository.exists({ root, query, cb ->
                cb.equal(root.join("books").get("title"), "Misery")
            } as QuerySpecification<ExistsAuthor>)
    }

    void "a distinct and ordered query"() {
        expect:
            repository.exists({ root, query, cb ->
                query.distinct(true)
                query.orderBy(cb.desc(root.get("name")))
                cb.equal(root.join("books").get("title"), "Carrie")
            } as QuerySpecification<ExistsAuthor>)
    }

    void "no matching root"() {
        expect:
            !repository.exists({ root, query, cb ->
                root.fetch("books", JoinType.LEFT)
                cb.equal(root.get("name"), "Nobody")
            } as QuerySpecification<ExistsAuthor>)
    }

    void "no predicate"() {
        expect:
            repository.exists({ root, query, cb -> null } as QuerySpecification<ExistsAuthor>)
    }
}
