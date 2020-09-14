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
package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.JoinPath
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.data.tck.entities.Book
import spock.lang.Unroll


class JpaJoinSpec extends AbstractDataSpec {


    void "test join on repository type that inherits from CrudRepository"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.Book;

@Repository
@Join("author")
interface MyInterface extends CrudRepository<Book, Long> {
}
"""
        )

        expect:"The repository to compile"
        repository != null
    }

    @Unroll
    void "test JPA projection across nested property path for #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Author, Long> {

    $returnType $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value().endsWith(suffix)

        where:
        method             | returnType | arguments      | suffix
        "findByBooksTitle" | "Author"   | "String title" | "JOIN author_.books author_books_ WHERE (author_books_.title = :p1)"
    }

    void "test join spec - list"() {
        given:
        def repository = buildRepository("test.MyInterface", '''
import io.micronaut.data.tck.entities.Book;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Book, Long> {

    @Join("author")
    List<Book> list();
    
    @Join("author")
    Book find(String title);
    
    @Join("author")
    Book findByTitle(String title);
    
    @Join("author")
    @Join("publisher")
    Book getByTitle(String title);
}
''')
        def builder = new JpaQueryBuilder()
        def entity = PersistentEntity.of(Book)
        def alias = builder.getAliasName(entity)
        def authorAlias = builder.getAliasName(JoinPath.of(entity.getPropertyByName("author")))
        def publisherAlias = builder.getAliasName(JoinPath.of(entity.getPropertyByName("publisher")))

        expect:
        repository.getRequiredMethod("list").synthesize(Query).value() ==
                "SELECT ${alias} FROM $Book.name AS ${alias} JOIN FETCH ${alias}.author $authorAlias"

        repository.getRequiredMethod("find", String).synthesize(Query).value() ==
                "SELECT ${alias} FROM $Book.name AS ${alias} JOIN FETCH ${alias}.author $authorAlias WHERE (${alias}.title = :p1)"

        repository.getRequiredMethod("findByTitle", String).synthesize(Query).value() ==
                "SELECT ${alias} FROM $Book.name AS ${alias} JOIN FETCH ${alias}.author $authorAlias WHERE (${alias}.title = :p1)"

        repository.getRequiredMethod("getByTitle", String).synthesize(Query).value() ==
                "SELECT ${alias} FROM $Book.name AS ${alias} JOIN FETCH ${alias}.publisher $publisherAlias JOIN FETCH ${alias}.author $authorAlias WHERE (${alias}.title = :p1)"

    }
}
