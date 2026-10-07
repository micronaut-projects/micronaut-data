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
package io.micronaut.data.processor.visitors

import io.micronaut.data.intercept.CountInterceptor
import io.micronaut.data.intercept.FindSliceInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.DataType
import io.micronaut.data.model.Pageable

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class RepositoryMethodResolutionSpec extends AbstractDataSpec {

    void "test a method without an implementation is reported against the method"() {
        when:
        buildRepository('test.PersonRepository', """
import io.micronaut.data.tck.entities.Person;

@Repository
interface PersonRepository extends GenericRepository<Person, Long> {

    Person somethingUnsupported(String name);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Unable to implement Repository method: test.PersonRepository.somethingUnsupported(String name). No possible implementations found.')
        !e.message.contains('Exception occurred while processing')
    }

    void "test the root entity is resolved from any lifecycle method of the repository"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository {

    long countByTitle(String title);

    boolean existsByTitle(String title);

    @Insert
    void insert(Book book);
}
""")

        when:
        def countByTitle = repository.getRequiredMethod("countByTitle", String)

        then:
        getDataInterceptor(countByTitle) == CountInterceptor.name
        getQuery(countByTitle) == 'SELECT COUNT(*) FROM `book` book_ WHERE (book_.`title` = ?)'
        getQuery(repository.getRequiredMethod("existsByTitle", String)).contains('FROM `book` book_ WHERE (book_.`title` = ?)')
    }

    void "test the root entity is not resolved from lifecycle methods of different entities"() {
        when:
        buildRepository('test.LibraryRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.H2)
interface LibraryRepository {

    long countById(Long id);

    @Insert
    void add(Book book);

    @Insert
    void add(Author author);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('long countById(Long id);')
        e.message.contains('Persistent entity is required')
    }

    void "test a type registered in the slice role uses the slice interceptor"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.tck.entities.Person;

@Repository
@RepositoryConfiguration(
    typeRoles = @TypeRole(role = TypeRole.SLICE, type = MySlice.class)
)
interface PersonRepository extends GenericRepository<Person, Long> {

    MySlice<Person> findByName(String name, Pageable pageable);

    org.springframework.data.domain.Slice<Person> findByAge(int age, org.springframework.data.domain.Pageable pageable);

    Slice<Person> findByNameLike(String name, Pageable pageable);
}

interface MySlice<T> {
}
""")

        expect:
        getDataInterceptor(repository.getRequiredMethod("findByName", String, Pageable)) == FindSliceInterceptor.name
        getDataInterceptor(repository.getRequiredMethod("findByAge", int, org.springframework.data.domain.Pageable)) == FindSliceInterceptor.name
        getDataInterceptor(repository.getRequiredMethod("findByNameLike", String, Pageable)) == FindSliceInterceptor.name
    }

    void "test the resolved data type of a type doesn't leak between compilations"() {
        when:
        def withoutTypeDef = buildMoneyRepository("")
        def withTypeDef = buildMoneyRepository("@TypeDef(type = DataType.JSON, classes = Money.class)")
        def withoutTypeDefAgain = buildMoneyRepository("")

        then:
        moneyParameterDataType(withoutTypeDef) == DataType.OBJECT
        moneyParameterDataType(withTypeDef) == DataType.JSON
        moneyParameterDataType(withoutTypeDefAgain) == DataType.OBJECT
    }

    private buildMoneyRepository(String typeDef) {
        return buildRepository('test.MoneyRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect = Dialect.H2)
$typeDef
interface MoneyRepository extends GenericRepository<Person, Long> {

    @Query("SELECT * FROM person WHERE name = :money")
    List<Person> findWithMoney(Money money);
}

class Money {
}
""")
    }

    private static DataType moneyParameterDataType(def repository) {
        def method = repository.findPossibleMethods("findWithMoney").findFirst().get()
        return getDataTypes(method.getAnnotationMetadata().getAnnotation(DataMethod))[0]
    }
}
