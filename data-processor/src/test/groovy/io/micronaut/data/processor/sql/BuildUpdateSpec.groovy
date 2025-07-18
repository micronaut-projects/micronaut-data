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


import io.micronaut.data.intercept.UpdateInterceptor
import io.micronaut.data.intercept.UpdateReturningManyInterceptor
import io.micronaut.data.intercept.UpdateReturningOneInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.async.UpdateAsyncInterceptor
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor
import io.micronaut.data.model.DataType
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Person
import spock.lang.PendingFeature
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.*

class BuildUpdateSpec extends AbstractDataSpec {

    @Unroll
    void "test build update for type #type"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    $type updatePerson(@Id Long id, String name);

    @Query("UPDATE person SET name = 'test' WHERE id = :id")
    $type customUpdate(Long id);

}
""")
        def method = repository.findPossibleMethods("updatePerson").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery == 'UPDATE `person` SET `name`=? WHERE (`id` = ?)'
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.concurrent.Future<java.lang.Void>' | UpdateAsyncInterceptor
        'io.reactivex.Completable'                    | UpdateReactiveInterceptor
        'io.reactivex.Single<Long>'                   | UpdateReactiveInterceptor
        'java.util.concurrent.CompletableFuture<Long>'| UpdateAsyncInterceptor
        'long'                                        | UpdateInterceptor
        'Long'                                        | UpdateInterceptor
        'void'                                        | UpdateInterceptor
    }

    @Unroll
    void "test build update returning for type #type"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query("UPDATE person SET name = 'test' WHERE id = :id RETURNING *")
    $type customUpdateReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customUpdateReturning").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery == "UPDATE person SET name = 'test' WHERE id = :id RETURNING *"
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Person>'                      | UpdateReturningManyInterceptor
        'Person'                                      | UpdateReturningOneInterceptor
    }

    @Unroll
    void "test build update returning for type #type text-block no-indent"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query(\"""
            UPDATE person SET name = 'test'
            WHERE id = :id
            RETURNING *
            \""")
    $type customUpdateReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customUpdateReturning").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery.replace('\n', ' ') == "UPDATE person SET name = 'test' WHERE id = :id RETURNING * "
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Person>'                      | UpdateReturningManyInterceptor
        'Person'                                      | UpdateReturningOneInterceptor
    }

    @Unroll
    void "test build update returning property for type #type"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query("UPDATE person SET name = 'test' WHERE id = :id RETURNING id")
    $type customUpdateReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customUpdateReturning").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery == "UPDATE person SET name = 'test' WHERE id = :id RETURNING id"
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Long>'                        | UpdateReturningManyInterceptor
        'Long'                                        | UpdateReturningOneInterceptor
    }

    @Unroll
    void "test build update returning property for type #type text-block no-indent"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    @Query(\"""
            UPDATE person SET name = 'test'
            WHERE id = :id
            RETURNING id
            \""")
    $type customUpdateReturning(Long id);

}
""")
        def method = repository.findPossibleMethods("customUpdateReturning").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery.replace('\n', ' ') == "UPDATE person SET name = 'test' WHERE id = :id RETURNING id "
        method.classValue(DataMethod, "interceptor").get() == interceptor

        where:
        type                                          | interceptor
        'java.util.List<Long>'                        | UpdateReturningManyInterceptor
        'Long'                                        | UpdateReturningOneInterceptor
    }

    @Unroll
    void "test build update with CTE"() {
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
            UPDATE person SET name = 'test'
            WHERE id = :id
            \""")
    void customUpdate(Long id);

}
""")
        def method = repository.findPossibleMethods("customUpdate").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery.replace('\n', ' ') == "WITH ids AS (SELECT id FROM person) UPDATE person SET name = 'test' WHERE id = :id "
        method.classValue(DataMethod, "interceptor").get() == UpdateInterceptor
    }

    @Unroll
    void "test build update with datasource set"() {
        given:
            def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Integer> {
    void updateById(int id, String theLongName, String title);
    void updateByIdInList(List<Integer> id, String title);
    void updateAll(java.util.List<Movie> movies);
}

${entity('Movie', [title: String, theLongName: String])}
""")
        def method = repository.findPossibleMethods(methodName).findFirst().get()

        expect:
        getQuery(method) == query
        getParameterPropertyPaths(method) == bindingPaths
        getParameterBindingIndexes(method) == binding

        where:
        methodName         | query                                                             | bindingPaths                               | binding
        'update'           | 'UPDATE `movie` SET `title`=?,`the_long_name`=? WHERE (`id` = ?)' | ['title', 'theLongName', 'id'] as String[] | ['-1', '-1', '-1'] as String[]
        'updateById'       | 'UPDATE `movie` SET `the_long_name`=?,`title`=? WHERE (`id` = ?)' | ['theLongName', 'title', 'id'] as String[] | ['1', '2', '0'] as String[]
        'updateAll'        | 'UPDATE `movie` SET `title`=?,`the_long_name`=? WHERE (`id` = ?)' | ['title', 'theLongName', 'id'] as String[] | ['-1', '-1', '-1'] as String[]
        'updateByIdInList' | 'UPDATE `movie` SET `title`=? WHERE (`id` IN (?))'                | ['title','id'] as String[]                 | ['1', '0'] as String[]
    }

    @Unroll
    void "test build update with custom ID"() {
        given:
        def repository = buildRepository('test.CompanyRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Company;
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface CompanyRepository extends CrudRepository<Company, Long> {
}
""")
        def method = repository.findPossibleMethods(methodName).findFirst().get()

        expect:
        getQuery(method) == query
        getParameterPropertyPaths(method) == bindingPaths
        getParameterBindingIndexes(method) == binding

        where:
        methodName | query                                                                        | bindingPaths                                       | binding
        'update'   | 'UPDATE `company` SET `last_updated`=?,`name`=?,`url`=? WHERE (`my_id` = ?)' | ['lastUpdated', 'name', 'url', 'myId'] as String[] | ['-1', '-1', '-1', '-1'] as String[]
    }

    void "test build update with embedded"() {
        given:
        def repository = buildRepository('test.CompanyRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface CompanyRepository extends CrudRepository<Restaurant, Long> {
}
""")
        def method = repository.findPossibleMethods("update").findFirst().get()
        def updateQuery = getQuery(method)
//        method = repository.findPossibleMethods("save").findFirst().get()
//        def insertQuery = method.stringValue(Query).get()

        expect:
        updateQuery == 'UPDATE `restaurant` SET `name`=?,`address_street`=?,`address_zip_code`=?,`hqaddress_street`=?,`hqaddress_zip_code`=? WHERE (`id` = ?)'
        getParameterPropertyPaths(method) == ["name", "address.street", "address.zipCode", "hqAddress.street", "hqAddress.zipCode", "id"] as String[]
    }


    void "test build update by ID"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends CrudRepository<Person, Long> {

    void updatePerson(@Id Long id, String name);
}
""")
        def method = repository.findPossibleMethods("updatePerson").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery == 'UPDATE `person` SET `name`=? WHERE (`id` = ?)'
    }

    void "test error message for update method with invalid return type"() {
        when:
        buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.MYSQL)
interface PersonRepository extends CrudRepository<Person, Long> {

    Person updatePerson(@Id Long id, String name);
}
""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Update methods only support void or number based return types")
    }


    void "test update by field with entity parameter"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect = Dialect.MYSQL)
interface PersonRepository extends GenericRepository<Person, Long> {

    void updateByName(String name, Person person);
}
""")

        def method = repository.findMethod("updateByName", String, Person).get()
        def updateQuery = getQuery(method)

        expect:
        updateQuery == 'UPDATE `person` SET `name`=?,`age`=?,`enabled`=?,`income`=? WHERE (`name` = ?)'
    }

    void "test AutoGenerated update method"() {
      given:
      def repository = buildRepository('test.StudentRepository', """
  import io.micronaut.data.jdbc.annotation.JdbcRepository;
  import io.micronaut.data.model.query.builder.sql.Dialect;
  import io.micronaut.data.tck.entities.Student;

  @JdbcRepository(dialect= Dialect.MYSQL)
  @io.micronaut.context.annotation.Executable
  interface StudentRepository extends CrudRepository<Student, Long> {

  }
  """)
      def method = repository.findPossibleMethods("update").findFirst().get()
      def updateQuery = getQuery(method)

      expect:
          updateQuery == 'UPDATE `student` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
    }

    void "test AutoGenerated update method JPA"() {
        given:
            def repository = buildRepository('test.StudentRepository', """
  import io.micronaut.data.jdbc.annotation.JdbcRepository;
  import io.micronaut.data.model.query.builder.sql.Dialect;
  import io.micronaut.data.tck.entities.StudentData;
  @JdbcRepository(dialect= Dialect.MYSQL)
  @io.micronaut.context.annotation.Executable
  interface StudentRepository extends CrudRepository<StudentData, Long> {
  }
  """)
            def method = repository.findPossibleMethods("update").findFirst().get()
            def updateQuery = getQuery(method)

        expect:
            updateQuery == 'UPDATE `student_data` SET `name`=?,`last_updated_time`=?,`version`=? WHERE (`id` = ? AND `version` = ?)'
    }

    void "test build update with multiple fields"() {
        given:
            def repository = buildRepository('test.CompanyRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Company;
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface CompanyRepository extends CrudRepository<Company, Long> {
    void updateByName(String name, @io.micronaut.context.annotation.Parameter("name") String xxx);
    void updateByLastUpdated(java.time.Instant lastUpdated, @io.micronaut.context.annotation.Parameter("lastUpdated") java.time.Instant xxx);
}
""")
        when:
            def updateByNameMethod = repository.findPossibleMethods("updateByName").findFirst().get()

        then:
            getQuery(updateByNameMethod) == "UPDATE `company` SET `name`=?,`last_updated`=? WHERE (`name` = ?)"
            getDataTypes(updateByNameMethod) == [DataType.STRING, DataType.TIMESTAMP, DataType.STRING]
            getParameterBindingIndexes(updateByNameMethod) == ["1", "-1", "0"]
            getParameterPropertyPaths(updateByNameMethod) == ["name", "lastUpdated", "name"]
            getParameterAutoPopulatedProperties(updateByNameMethod) == ["", "lastUpdated", ""]
            getParameterRequiresPreviousPopulatedValueProperties(updateByNameMethod) == ["", "", ""]

        when:
            def updateByLastUpdatedMethod = repository.findPossibleMethods("updateByLastUpdated").findFirst().get()

        then:
            getQuery(updateByLastUpdatedMethod) == "UPDATE `company` SET `last_updated`=? WHERE (`last_updated` = ?)"
            getDataTypes(updateByLastUpdatedMethod) == [DataType.TIMESTAMP, DataType.TIMESTAMP]
            getParameterBindingIndexes(updateByLastUpdatedMethod) == ["-1", "0"]
            getParameterPropertyPaths(updateByLastUpdatedMethod) == ["lastUpdated", "lastUpdated"]
            getParameterAutoPopulatedProperties(updateByLastUpdatedMethod) == ["lastUpdated", "lastUpdated"]
            getParameterRequiresPreviousPopulatedValueProperties(updateByLastUpdatedMethod) == ["", ""]
    }

    void "test build update relation"() {
        given:
            def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface BookRepository extends CrudRepository<Book, Long> {

    @Query("UPDATE Book SET author_id = :author WHERE id = :id")
    long updateAuthorCustom(Long id, Author author);

    long updateAuthor(@Id Long id, Author author);

}
""")
        when:
            def updateAuthorCustomMethod = repository.findPossibleMethods("updateAuthorCustom").findFirst().get()
        then:
            getQuery(updateAuthorCustomMethod) == 'UPDATE Book SET author_id = :author WHERE id = :id'
            getRawQuery(updateAuthorCustomMethod) == 'UPDATE Book SET author_id = ? WHERE id = ?'
            getParameterBindingIndexes(updateAuthorCustomMethod) == ["1", "0"] as String[]
            getDataTypes(updateAuthorCustomMethod) == [DataType.ENTITY, DataType.LONG]
            getParameterPropertyPaths(updateAuthorCustomMethod) == ["author", "id"] as String[]
            getResultDataType(updateAuthorCustomMethod) == DataType.LONG
        when:
            def updateAuthorMethod = repository.findPossibleMethods("updateAuthor").findFirst().get()
        then:
            getQuery(updateAuthorMethod) == 'UPDATE `book` SET `author_id`=?,`last_updated`=? WHERE (`id` = ?)'
            getParameterBindingIndexes(updateAuthorMethod) == ["1", "-1", "0"] as String[]
            getParameterBindingPaths(updateAuthorMethod) == ["id", "", ""] as String[]
            getParameterPropertyPaths(updateAuthorMethod) == ["author.id", "lastUpdated", "id"] as String[]
            getDataTypes(updateAuthorMethod) == [DataType.LONG, DataType.TIMESTAMP, DataType.LONG]
            getResultDataType(updateAuthorMethod) == DataType.LONG

    }

    void "test build update returning no supported"() {
        when:
            buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface BookRepository extends GenericRepository<Book, Long> {

    Book updateReturning(Book book);

}
""")
        then:
            def ex = thrown(RuntimeException)
            ex.message.contains("Dialect: MYSQL doesn't support UPDATE ... RETURNING clause")
    }

    void "POSTGRES test build update returning "() {
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

    Book updateReturning(Book book);

}
""")
        when:
            def updateReturningCustomMethod = repository.findPossibleMethods("updateReturning").findFirst().get()
        then:
            getQuery(updateReturningCustomMethod) == 'UPDATE "book" SET "author_id"=?,"genre_id"=?,"title"=?,"total_pages"=?,"publisher_id"=?,"last_updated"=? WHERE ("id" = ?) RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"'
            getDataResultType(updateReturningCustomMethod) == "io.micronaut.data.tck.entities.Book"
            getParameterPropertyPaths(updateReturningCustomMethod) == ["author.id", "genre.id", "title", "totalPages", "publisher.id", "lastUpdated", "id"] as String[]
            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateEntityInterceptor"
            getResultDataType(updateReturningCustomMethod) == DataType.ENTITY
    }

    void "POSTGRES test build update returning property"() {
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

    String updateReturningTitle(Book book);

}
""")
        when:
            def updateReturningCustomMethod = repository.findPossibleMethods("updateReturningTitle").findFirst().get()
        then:
            getQuery(updateReturningCustomMethod) == 'UPDATE "book" SET "author_id"=?,"genre_id"=?,"title"=?,"total_pages"=?,"publisher_id"=?,"last_updated"=? WHERE ("id" = ?) RETURNING "title"'
            getParameterPropertyPaths(updateReturningCustomMethod) == ["author.id", "genre.id", "title", "totalPages", "publisher.id", "lastUpdated", "id"] as String[]
            getDataResultType(updateReturningCustomMethod) == "java.lang.String"
            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateReturningOneInterceptor"
            getResultDataType(updateReturningCustomMethod) == DataType.STRING
    }

    void "POSTGRES test build update returning property 2"() {
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

    LocalDateTime updateReturningLastUpdated(@Id Long id, String title);

}
""")
        when:
            def updateReturningCustomMethod = repository.findPossibleMethods("updateReturningLastUpdated").findFirst().get()
        then:
            getQuery(updateReturningCustomMethod) == 'UPDATE "book" SET "title"=?,"last_updated"=? WHERE ("id" = ?) RETURNING "last_updated"'
            getParameterPropertyPaths(updateReturningCustomMethod) == ["title", "lastUpdated", "id"] as String[]
            getDataResultType(updateReturningCustomMethod) == "java.time.LocalDateTime"
            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateReturningOneInterceptor"
            getResultDataType(updateReturningCustomMethod) == DataType.TIMESTAMP
    }

    void "POSTGRES test build update returning property 3"() {
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

    LocalDateTime updateByIdReturningLastUpdated(Long id, String title);

}
""")
        when:
            def updateReturningCustomMethod = repository.findPossibleMethods("updateByIdReturningLastUpdated").findFirst().get()
        then:
            getQuery(updateReturningCustomMethod) == 'UPDATE "book" SET "title"=?,"last_updated"=? WHERE ("id" = ?) RETURNING "last_updated"'
            getParameterPropertyPaths(updateReturningCustomMethod) == ["title", "lastUpdated", "id"] as String[]
            getDataResultType(updateReturningCustomMethod) == "java.time.LocalDateTime"
            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateReturningOneInterceptor"
            getResultDataType(updateReturningCustomMethod) == DataType.TIMESTAMP
    }

    void "POSTGRES test build update all"() {
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

    List<Book> updateReturning(Long authorId);

}
""")
        when:
            def updateReturningCustomMethod = repository.findPossibleMethods("updateReturning").findFirst().get()
        then:
            getQuery(updateReturningCustomMethod) == 'UPDATE "book" SET "author_id"=?,"last_updated"=? RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"'
            getParameterPropertyPaths(updateReturningCustomMethod) == ["author.id", "lastUpdated"] as String[]
            getDataResultType(updateReturningCustomMethod) == "io.micronaut.data.tck.entities.Book"
            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateReturningManyInterceptor"
            getResultDataType(updateReturningCustomMethod) == DataType.ENTITY
    }

    void "POSTGRES test build update all 2"() {
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

    List<Book> updateReturning(List<Book> books);

}
""")
        when:
            def updateReturningCustomMethod = repository.findPossibleMethods("updateReturning").findFirst().get()
        then:
            getQuery(updateReturningCustomMethod) == 'UPDATE "book" SET "author_id"=?,"genre_id"=?,"title"=?,"total_pages"=?,"publisher_id"=?,"last_updated"=? WHERE ("id" = ?) RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"'
            getParameterPropertyPaths(updateReturningCustomMethod) == ["author.id", "genre.id", "title", "totalPages", "publisher.id", "lastUpdated", "id"] as String[]
            getDataResultType(updateReturningCustomMethod) == "io.micronaut.data.tck.entities.Book"
            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateAllEntitiesInterceptor"
            getResultDataType(updateReturningCustomMethod) == DataType.ENTITY
    }

    void "test build update with system Version field"() {
        given:
        def repository = buildRepository('test.TestRepository', """
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@MappedEntity
class Article {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private Double price;

    @Version
    @GeneratedValue
    private Long version;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Double getPrice() {
        return price;
    }
    public void setPrice(Double price) {
        this.price = price;
    }
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
}
@JdbcRepository(dialect = Dialect.ORACLE)
interface TestRepository extends CrudRepository<Article, Long> {
}
""")
        def method = repository.findPossibleMethods("update").findFirst().get()
        def updateQuery = getQuery(method)

        expect:
        // Field version is not being updated as it's marked as system field ie system generated value
        updateQuery == 'UPDATE "ARTICLE" SET "NAME"=?,"PRICE"=? WHERE ("ID" = ? AND "VERSION" = ?)'
    }

    void "POSTGRES test update with tenant id"() {
        given:
            def repository = buildRepository('test.AccountRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Account;

@JdbcRepository(dialect= Dialect.POSTGRES)
interface AccountRepository extends CrudRepository<Account, Long> {

    List<Account> updateReturning(List<Account> books);

    void updateByIdAndTenancy(Long id, String tenancy, String name);

    void updateAccount1(@Id Long id, String tenancy, String name);
    void updateAccount2(@Id Long id, String name);

    void updateById(@Id Long id, String name);
    void update(@Id Long id, String name);

}
""")
        when:
            def updateReturningCustomMethod = repository.findPossibleMethods("updateReturning").findFirst().get()
        then:
            getQuery(updateReturningCustomMethod) == 'UPDATE "account" SET "name"=?,"tenancy"=? WHERE ("id" = ? AND "tenancy" = ?) RETURNING "id","name","tenancy"'
            getParameterPropertyPaths(updateReturningCustomMethod) == ["name", "tenancy", "id", "tenancy"] as String[]
            getDataResultType(updateReturningCustomMethod) == "io.micronaut.data.tck.entities.Account"
            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateAllEntitiesInterceptor"
            getResultDataType(updateReturningCustomMethod) == DataType.ENTITY

        when:
            def updateByIdAndTenancyMethod = repository.findPossibleMethods("updateByIdAndTenancy").findFirst().get()
        then:
            getQuery(updateByIdAndTenancyMethod) == 'UPDATE "account" SET "name"=?,"tenancy"=? WHERE ("id" = ? AND "tenancy" = ? AND "tenancy" = ?)'
            getParameterPropertyPaths(updateByIdAndTenancyMethod) == ["name", "tenancy", "id", "tenancy", "tenancy"] as String[]
            getDataResultType(updateByIdAndTenancyMethod) == "void"
            getDataInterceptor(updateByIdAndTenancyMethod) == "io.micronaut.data.intercept.UpdateInterceptor"
            getResultDataType(updateByIdAndTenancyMethod) == null

        when:
            def updateAccount1 = repository.findPossibleMethods("updateAccount1").findFirst().get()
        then:
            getQuery(updateAccount1) == 'UPDATE "account" SET "tenancy"=?,"name"=? WHERE ("id" = ? AND "tenancy" = ?)'
            getParameterPropertyPaths(updateAccount1) == ["tenancy", "name", "id", "tenancy"] as String[]
            getDataResultType(updateAccount1) == "void"
            getDataInterceptor(updateAccount1) == "io.micronaut.data.intercept.UpdateInterceptor"
            getResultDataType(updateAccount1) == null

        when:
            def updateAccount2 = repository.findPossibleMethods("updateAccount2").findFirst().get()
        then:
            getQuery(updateAccount2) == 'UPDATE "account" SET "name"=?,"tenancy"=? WHERE ("id" = ? AND "tenancy" = ?)'
            getParameterPropertyPaths(updateAccount2) == ["name", "tenancy", "id", "tenancy"] as String[]
            getDataResultType(updateAccount2) == "void"
            getDataInterceptor(updateAccount2) == "io.micronaut.data.intercept.UpdateInterceptor"
            getResultDataType(updateAccount2) == null

        when:
            def updateById = repository.findPossibleMethods("updateById").findFirst().get()
        then:
            getQuery(updateById) == 'UPDATE "account" SET "name"=?,"tenancy"=? WHERE ("id" = ? AND "tenancy" = ?)'
            getParameterPropertyPaths(updateById) == ["name", "tenancy", "id", "tenancy"] as String[]
            getDataResultType(updateById) == "void"
            getDataInterceptor(updateById) == "io.micronaut.data.intercept.UpdateInterceptor"
            getResultDataType(updateById) == null

        when:
            def update = repository.findMethod("update", Long, String).get()
        then:
            getQuery(update) == 'UPDATE "account" SET "name"=?,"tenancy"=? WHERE ("id" = ? AND "tenancy" = ?)'
            getParameterPropertyPaths(update) == ["name", "tenancy", "id", "tenancy"] as String[]
            getDataResultType(update) == "void"
            getDataInterceptor(update) == "io.micronaut.data.intercept.UpdateInterceptor"
            getResultDataType(update) == null
    }

//    void "ORACLE test build update returning "() {
//        given:
//            def repository = buildRepository('test.BookRepository', """
//import io.micronaut.data.jdbc.annotation.JdbcRepository;
//import io.micronaut.data.model.query.builder.sql.Dialect;
//import io.micronaut.data.repository.GenericRepository;
//import io.micronaut.data.tck.entities.Book;
//import io.micronaut.data.tck.entities.Author;
//
//@JdbcRepository(dialect= Dialect.ORACLE)
//@io.micronaut.context.annotation.Executable
//interface BookRepository extends GenericRepository<Book, Long> {
//
//    Book updateReturning(Book book);
//
//}
//""")
//        when:
//            def updateReturningCustomMethod = repository.findPossibleMethods("updateReturning").findFirst().get()
//        then:
//            getQuery(updateReturningCustomMethod) == 'UPDATE "BOOK" SET "AUTHOR_ID"=?,"GENRE_ID"=?,"TITLE"=?,"TOTAL_PAGES"=?,"PUBLISHER_ID"=?,"LAST_UPDATED"=? WHERE ("ID" = ?) RETURNING "ID","AUTHOR_ID","GENRE_ID","TITLE","TOTAL_PAGES","PUBLISHER_ID","LAST_UPDATED" INTO "ID","AUTHOR_ID","GENRE_ID","TITLE","TOTAL_PAGES","PUBLISHER_ID","LAST_UPDATED"'
//            getDataResultType(updateReturningCustomMethod) == "io.micronaut.data.tck.entities.Book"
//            getParameterPropertyPaths(updateReturningCustomMethod) == ["author.id", "genre.id", "title", "totalPages", "publisher.id", "lastUpdated", "id"] as String[]
//            getDataInterceptor(updateReturningCustomMethod) == "io.micronaut.data.intercept.UpdateReturningInterceptor"
//    }

}
