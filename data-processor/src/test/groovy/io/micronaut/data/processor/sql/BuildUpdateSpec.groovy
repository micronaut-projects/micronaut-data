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
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.async.UpdateAsyncInterceptor
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor
import io.micronaut.data.model.DataType
import io.micronaut.data.processor.visitors.AbstractDataSpec
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
            getQuery(updateByNameMethod) == "UPDATE `company` SET `last_updated`=? WHERE (`name` = ?)"
            getDataTypes(updateByNameMethod) == [DataType.TIMESTAMP, DataType.STRING]
            getParameterBindingIndexes(updateByNameMethod) == ["-1", "0"]
            getParameterPropertyPaths(updateByNameMethod) == ["lastUpdated", "name"]
            getParameterAutoPopulatedProperties(updateByNameMethod) == ["lastUpdated", ""]
            getParameterRequiresPreviousPopulatedValueProperties(updateByNameMethod) == ["", ""]

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
        when:
            def updateAuthorMethod = repository.findPossibleMethods("updateAuthor").findFirst().get()
        then:
            getQuery(updateAuthorMethod) == 'UPDATE `book` SET `author_id`=?,`last_updated`=? WHERE (`id` = ?)'
            getParameterBindingIndexes(updateAuthorMethod) == ["1", "-1", "0"] as String[]
            getParameterBindingPaths(updateAuthorMethod) == ["id", "", ""] as String[]
            getParameterPropertyPaths(updateAuthorMethod) == ["author.id", "lastUpdated", "id"] as String[]
            getDataTypes(updateAuthorMethod) == [DataType.LONG, DataType.TIMESTAMP, DataType.LONG]

    }
}
