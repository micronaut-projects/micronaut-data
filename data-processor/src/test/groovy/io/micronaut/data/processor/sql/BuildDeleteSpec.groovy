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
package io.micronaut.data.processor.sql

import io.micronaut.data.model.DataType
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery
import static io.micronaut.data.processor.visitors.TestUtils.getQueryParts
import static io.micronaut.data.processor.visitors.TestUtils.getRawQuery

class BuildDeleteSpec extends AbstractDataSpec {

    @Unroll
    void "test build delete"() {
        given:
            def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Integer> {

    @Query("DELETE FROM movie WHERE title = :title")
    int deleteCustom(java.util.List<Movie> movies);

    @Query("DELETE FROM movie WHERE title = :title")
    int deleteCustomSingle(Movie movie);

}

${entity('Movie', [title: String, theLongName: String])}
""")
            def method = repository.findPossibleMethods(methodName).findFirst().get()

        expect:
            getRawQuery(method) == query
            getParameterPropertyPaths(method)  == propertyPaths
            getParameterBindingIndexes(method) == parameterIndexes

        where:
            methodName           | query                               | propertyPaths         | parameterIndexes
            'deleteCustom'       | 'DELETE FROM movie WHERE title = ?' | ['title'] as String[] | ['-1'] as String[]
            'deleteCustomSingle' | 'DELETE FROM movie WHERE title = ?' | ['title'] as String[] | ['-1'] as String[]
    }

    void  "test build delete query"() {
        given:
            def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Integer> {
}

${entity('Movie', [title: String, theLongName: String])}
""")
            def method = repository.findMethod('deleteAll', Iterable.class).get()

        expect:
            getQuery(method) == 'DELETE  FROM `movie`  WHERE (`id` IN (?))'
            getQueryParts(method) == ['DELETE  FROM `movie`  WHERE (`id` IN (', '))']
            getParameterPropertyPaths(method) == ['id'] as String[]
            getParameterBindingIndexes(method) == ['-1'] as String[]
    }

    void "test build delete relation"() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface BookRepository extends CrudRepository<Book, Long> {

    int deleteByIdAndAuthorId(Long id, Long authorId);
    
    @Query("DELETE  FROM `book`  WHERE (`id` = :id AND `author_id` = :authorId)")
    int deleteAllByIdAndAuthorId(Long id, Long authorId);
    
    int deleteAllByAuthor(Author author);

}
""")
        when:
            def deleteByIdAndAuthorIdMethod = repository.findPossibleMethods("deleteByIdAndAuthorId").findFirst().get()
        then:
            getQuery(deleteByIdAndAuthorIdMethod) == 'DELETE  FROM `book`  WHERE (`id` = ? AND `author_id` = ?)'
            getParameterBindingIndexes(deleteByIdAndAuthorIdMethod) == ["0", "1"] as String[]
            getParameterBindingPaths(deleteByIdAndAuthorIdMethod) == ["", ""] as String[]
            getParameterPropertyPaths(deleteByIdAndAuthorIdMethod) == ["id", "author.id"] as String[]
            getDataTypes(deleteByIdAndAuthorIdMethod) == [DataType.LONG, DataType.LONG]
            getDataInterceptor(deleteByIdAndAuthorIdMethod) == "io.micronaut.data.intercept.DeleteAllInterceptor"
        when:
            def deleteAllByIdAndAuthorIdMethod = repository.findPossibleMethods("deleteAllByIdAndAuthorId").findFirst().get()
        then:
            getQuery(deleteAllByIdAndAuthorIdMethod) == 'DELETE  FROM `book`  WHERE (`id` = :id AND `author_id` = :authorId)'
            getRawQuery(deleteAllByIdAndAuthorIdMethod) == 'DELETE  FROM `book`  WHERE (`id` = ? AND `author_id` = ?)'
            getParameterBindingIndexes(deleteAllByIdAndAuthorIdMethod) == ["0", "1"] as String[]
            getParameterBindingPaths(deleteAllByIdAndAuthorIdMethod) == ["", ""] as String[]
            getParameterPropertyPaths(deleteAllByIdAndAuthorIdMethod) == ["id", ""] as String[]
            getDataTypes(deleteAllByIdAndAuthorIdMethod) == [DataType.LONG, DataType.LONG]
            getDataInterceptor(deleteAllByIdAndAuthorIdMethod) == "io.micronaut.data.intercept.DeleteAllInterceptor"
        when:
            def deleteAllByAuthor = repository.findPossibleMethods("deleteAllByAuthor").findFirst().get()
        then:
            getQuery(deleteAllByAuthor) == 'DELETE  FROM `book`  WHERE (`author_id` = ?)'
            getParameterBindingIndexes(deleteAllByAuthor) == ["0"] as String[]
            getParameterBindingPaths(deleteAllByAuthor) == ["id"] as String[]
            getParameterPropertyPaths(deleteAllByAuthor) == ["author.id"] as String[]
            getDataInterceptor(deleteAllByAuthor) == "io.micronaut.data.intercept.DeleteAllInterceptor"
    }

    void  "test build delete query with DataTransformer"() {
        given:
        def repository = buildRepository('test.UuidEntityRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface UuidEntityRepository extends GenericRepository<UuidEntity, UUID> {
    void deleteById(UUID id);

    void deleteByIdGreaterThan(UUID id);
}

@MappedEntity
class UuidEntity {

    @Id
    @AutoPopulated
    @DataTransformer(read = "BIN_TO_UUID(@.id)", write = "UUID_TO_BIN(?)")
    private UUID id;

    private String name;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

""")
        def deleteByIdMethod = repository.findMethod('deleteById', UUID).get()
        def deleteByIdQuery = getQuery(deleteByIdMethod)
        def deleteByIdGreaterThanMethod = repository.findMethod('deleteByIdGreaterThan', UUID).get()
        def deleteByIdGreaterThanQuery = getQuery(deleteByIdGreaterThanMethod)
        expect:
        deleteByIdQuery == 'DELETE  FROM `uuid_entity`  WHERE (`id` = UUID_TO_BIN(?))'
        deleteByIdGreaterThanQuery == 'DELETE  FROM `uuid_entity`  WHERE (`id` > UUID_TO_BIN(?))'
    }

}
