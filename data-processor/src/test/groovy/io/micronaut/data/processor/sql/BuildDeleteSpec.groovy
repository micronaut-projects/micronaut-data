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

import io.micronaut.data.intercept.DeleteAllInterceptor
import io.micronaut.data.intercept.DeleteReturningManyInterceptor
import io.micronaut.data.intercept.DeleteReturningOneInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.DataType
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery
import static io.micronaut.data.processor.visitors.TestUtils.getQueryParts
import static io.micronaut.data.processor.visitors.TestUtils.getRawQuery
import static io.micronaut.data.processor.visitors.TestUtils.getResultDataType
import static io.micronaut.data.processor.visitors.TestUtils.getResultDataType
import static io.micronaut.data.processor.visitors.TestUtils.getResultDataType
import static io.micronaut.data.processor.visitors.TestUtils.getResultDataType
import static io.micronaut.data.processor.visitors.TestUtils.getResultDataType
import static io.micronaut.data.processor.visitors.TestUtils.getResultDataType

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

    void "POSTGRES test build delete returning "() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface BookRepository extends GenericRepository<Book, Long> {

    Book deleteReturning(Book book);

}
""")
        when:
            def deleteReturningCustomMethod = repository.findPossibleMethods("deleteReturning").findFirst().get()
        then:
            getQuery(deleteReturningCustomMethod) == 'DELETE  FROM "book"  WHERE ("id" = ?) RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"'
            getDataResultType(deleteReturningCustomMethod) == "io.micronaut.data.tck.entities.Book"
            getParameterPropertyPaths(deleteReturningCustomMethod) == ["id"] as String[]
            getDataInterceptor(deleteReturningCustomMethod) == "io.micronaut.data.intercept.DeleteOneInterceptor"
            getResultDataType(deleteReturningCustomMethod) == DataType.ENTITY
    }

    void "POSTGRES test build delete returning property"() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface BookRepository extends GenericRepository<Book, Long> {

    String deleteReturningTitle(Book book);

}
""")
        when:
            def deleteReturningCustomMethod = repository.findPossibleMethods("deleteReturningTitle").findFirst().get()
        then:
            getQuery(deleteReturningCustomMethod) == 'DELETE  FROM "book"  WHERE ("id" = ?) RETURNING "title"'
            getParameterPropertyPaths(deleteReturningCustomMethod) == ["id"] as String[]
            getDataResultType(deleteReturningCustomMethod) == "java.lang.String"
            getDataInterceptor(deleteReturningCustomMethod) == "io.micronaut.data.intercept.DeleteReturningOneInterceptor"
            getResultDataType(deleteReturningCustomMethod) == DataType.STRING
    }

    void "POSTGRES test build delete returning property 2"() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import java.time.LocalDateTime;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface BookRepository extends GenericRepository<Book, Long> {

    LocalDateTime deleteReturningLastUpdated(Long id, String title);

}
""")
        when:
            def deleteReturningCustomMethod = repository.findPossibleMethods("deleteReturningLastUpdated").findFirst().get()
        then:
            getQuery(deleteReturningCustomMethod) == 'DELETE  FROM "book"  WHERE ("id" = ? AND "title" = ?) RETURNING "last_updated"'
            getParameterPropertyPaths(deleteReturningCustomMethod) == ["id", "title"] as String[]
            getDataResultType(deleteReturningCustomMethod) == "java.time.LocalDateTime"
            getDataInterceptor(deleteReturningCustomMethod) == "io.micronaut.data.intercept.DeleteReturningOneInterceptor"
            getResultDataType(deleteReturningCustomMethod) == DataType.TIMESTAMP
    }

    void "POSTGRES test build delete returning property 3"() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import java.time.LocalDateTime;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface BookRepository extends GenericRepository<Book, Long> {

    LocalDateTime deleteByIdAndTitleReturningLastUpdated(Long id, String title);

}
""")
        when:
            def deleteReturningCustomMethod = repository.findPossibleMethods("deleteByIdAndTitleReturningLastUpdated").findFirst().get()
        then:
            getQuery(deleteReturningCustomMethod) == 'DELETE  FROM "book"  WHERE ("id" = ? AND "title" = ?) RETURNING "last_updated"'
            getParameterPropertyPaths(deleteReturningCustomMethod) == ["id", "title"] as String[]
            getDataResultType(deleteReturningCustomMethod) == "java.time.LocalDateTime"
            getDataInterceptor(deleteReturningCustomMethod) == "io.micronaut.data.intercept.DeleteReturningOneInterceptor"
            getResultDataType(deleteReturningCustomMethod) == DataType.TIMESTAMP
    }

    void "POSTGRES test build delete all"() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;
import java.time.LocalDateTime;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface BookRepository extends GenericRepository<Book, Long> {

    List<Book> deleteReturning(Long authorId);

}
""")
        when:
            def deleteReturningCustomMethod = repository.findPossibleMethods("deleteReturning").findFirst().get()
        then:
            getQuery(deleteReturningCustomMethod) == 'DELETE  FROM "book"  WHERE ("author_id" = ?) RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"'
            getParameterPropertyPaths(deleteReturningCustomMethod) == ["author.id"] as String[]
            getDataResultType(deleteReturningCustomMethod) == "io.micronaut.data.tck.entities.Book"
            getDataInterceptor(deleteReturningCustomMethod) == "io.micronaut.data.intercept.DeleteReturningManyInterceptor"
            getResultDataType(deleteReturningCustomMethod) == DataType.ENTITY
    }

    void "POSTGRES test build delete all 2"() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface BookRepository extends GenericRepository<Book, Long> {

    List<Book> deleteReturning(List<Book> books);

}
""")
        when:
            def deleteReturningMethod = repository.findPossibleMethods("deleteReturning").findFirst().get()
        then:
            getQuery(deleteReturningMethod) == 'DELETE  FROM "book"  WHERE ("id" IN (?)) RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"'
            getParameterPropertyPaths(deleteReturningMethod) == ["id"] as String[]
            getDataResultType(deleteReturningMethod) == "io.micronaut.data.tck.entities.Book"
            getDataInterceptor(deleteReturningMethod) == "io.micronaut.data.intercept.DeleteAllReturningInterceptor"
            getResultDataType(deleteReturningMethod) == DataType.ENTITY
    }

    void "POSTGRES test build delete with tenant id"() {
        given:
            def repository = buildRepository('test.AccountRepository', """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Account;

@JdbcRepository(dialect= Dialect.POSTGRES)
interface AccountRepository extends CrudRepository<Account, Long> {

    List<Account> deleteReturning(Long id);

}
""")
        when:
            def deleteReturningCustomMethod = repository.findPossibleMethods("deleteReturning").findFirst().get()
        then:
            getQuery(deleteReturningCustomMethod) == 'DELETE  FROM "account"  WHERE ("id" = ? AND "tenancy" = ?) RETURNING "id","name","tenancy"'
            getParameterPropertyPaths(deleteReturningCustomMethod) == ["id", "tenancy"] as String[]
            getDataResultType(deleteReturningCustomMethod) == "io.micronaut.data.tck.entities.Account"
            getDataInterceptor(deleteReturningCustomMethod) == "io.micronaut.data.intercept.DeleteReturningManyInterceptor"
            getResultDataType(deleteReturningCustomMethod) == DataType.ENTITY

        when:
            def deleteOne = repository.findPossibleMethods("delete").findFirst().get()
        then:
            getQuery(deleteOne) == 'DELETE  FROM "account"  WHERE ("id" = ? AND "tenancy" = ?)'
            getParameterPropertyPaths(deleteOne) == ["id", "tenancy"] as String[]
            getDataResultType(deleteOne) == "void"
            getDataInterceptor(deleteOne) == "io.micronaut.data.intercept.DeleteOneInterceptor"
            getResultDataType(deleteOne) == null

        when:
            def deleteAll = repository.findPossibleMethods("deleteAll").findFirst().get()
        then:
            getQuery(deleteAll) == 'DELETE  FROM "account"  WHERE ("tenancy" = ?)'
            getParameterPropertyPaths(deleteAll) == ["tenancy"] as String[]
            getDataResultType(deleteAll) == "void"
            getDataInterceptor(deleteAll) == "io.micronaut.data.intercept.DeleteAllInterceptor"
            getResultDataType(deleteAll) == null

        when:
            def deleteEntities = repository.findMethod("deleteAll", Iterable).get()
        then:
            getQuery(deleteEntities) == 'DELETE  FROM "account"  WHERE ("id" IN (?) AND "tenancy" = ?)'
            getParameterPropertyPaths(deleteEntities) == ["id", "tenancy"] as String[]
            getDataResultType(deleteEntities) == "void"
            getDataInterceptor(deleteEntities) == "io.micronaut.data.intercept.DeleteAllInterceptor"
            getResultDataType(deleteEntities) == null

        when:
            def deleteById = repository.findPossibleMethods("deleteById").findFirst().get()
        then:
            getQuery(deleteById) == 'DELETE  FROM "account"  WHERE ("id" = ? AND "tenancy" = ?)'
            getParameterPropertyPaths(deleteById) == ["id", "tenancy"] as String[]
            getDataResultType(deleteById) == "void"
            getDataInterceptor(deleteById) == "io.micronaut.data.intercept.DeleteAllInterceptor"
            getResultDataType(deleteById) == null
    }

    @Unroll
    void "test build delete returning for type #type"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query("DELETE FROM person RETURNING *")
    $type customDeleteReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customDeleteReturning").findFirst().get()
        def deleteQuery = getQuery(method)

        expect:
        deleteQuery == "DELETE FROM person RETURNING *"
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Person>'                      | DeleteReturningManyInterceptor
        'Person'                                      | DeleteReturningOneInterceptor
    }

    @Unroll
    void "test build delete returning for type #type text-block no-indent"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query(\"""
            DELETE FROM person
            RETURNING *
            \""")
    $type customDeleteReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customDeleteReturning").findFirst().get()
        def deleteQuery = getQuery(method)

        expect:
        deleteQuery.replace('\n', ' ') == "DELETE FROM person RETURNING * "
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Person>'                      | DeleteReturningManyInterceptor
        'Person'                                      | DeleteReturningOneInterceptor
    }

    @Unroll
    void "test build delete returning property for type #type"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query("DELETE FROM person RETURNING id")
    $type customDeleteReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customDeleteReturning").findFirst().get()
        def deleteQuery = getQuery(method)

        expect:
        deleteQuery == "DELETE FROM person RETURNING id"
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Long>'                        | DeleteReturningManyInterceptor
        'Long'                                        | DeleteReturningOneInterceptor
    }

    @Unroll
    void "test build delete returning property for type #type text-block no-indent"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query(\"""
            DELETE FROM person
            RETURNING id
            \""")
    $type customDeleteReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customDeleteReturning").findFirst().get()
        def deleteQuery = getQuery(method)

        expect:
        deleteQuery.replace('\n', ' ') == "DELETE FROM person RETURNING id "
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Long>'                        | DeleteReturningManyInterceptor
        'Long'                                        | DeleteReturningOneInterceptor
    }

    @Unroll
    void "test build delete with CTE"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query(\"""
            WITH ids AS (SELECT id FROM person)
            DELETE FROM person
            WHERE id = :id
            \""")
    void customDelete(Long id);

}
""")
        def method = repository.findPossibleMethods("customDelete").findFirst().get()
        def deleteQuery = getQuery(method)

        expect:
        deleteQuery.replace('\n', ' ') == "WITH ids AS (SELECT id FROM person) DELETE FROM person WHERE id = :id "
        method.classValue(DataMethod, "interceptor").get() == DeleteAllInterceptor
    }
}
