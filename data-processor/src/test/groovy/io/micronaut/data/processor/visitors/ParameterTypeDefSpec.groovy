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


import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.DataType

import static io.micronaut.data.processor.visitors.TestUtils.*

class ParameterTypeDefSpec extends AbstractDataSpec {

    void "test parameter type def resolved from entity"() {
        given:
        def repository = buildRepository('test.PersonRepository', '''
import java.util.UUID;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.repository.GenericRepository;

@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    implicitQueries = false,
    namedParameters = false
)
@Repository
@io.micronaut.context.annotation.Executable
interface PersonRepository extends GenericRepository<Person, UUID> {
    List<Person> findByIdIn(List<UUID> id);


    void deleteAll(Iterable<Person> entities);
}

@MappedEntity
class Person {
    @Id
    @TypeDef(type = DataType.OBJECT)
    protected UUID id = UUID.randomUUID();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
''')

        expect:
        getDataTypes(repository.getRequiredMethod("findByIdIn", List)
            .getAnnotationMetadata()
            .getAnnotation(DataMethod))[0] == DataType.OBJECT

        getDataTypes(repository.getRequiredMethod("deleteAll", Iterable)
            .getAnnotationMetadata()
            .getAnnotation(DataMethod)).length == 0

    }

    void "test parameter type for query"() {
        given:
        def repository = buildRepository('test.BookRepository', '''
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.tck.repositories.AuthorRepository;

@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    implicitQueries = false,
    namedParameters = false
)
@Repository
@io.micronaut.context.annotation.Executable
abstract class BookRepository extends io.micronaut.data.tck.repositories.BookRepository {

    public BookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Query(value = "select count(*) from book b where b.title like :title and b.total_pages > :pages", nativeQuery = true)
    abstract int countNativeByTitleWithPagesGreaterThan(String title, int pages);
}

''')

        when:
        def values = getDataTypes(repository.getRequiredMethod("countNativeByTitleWithPagesGreaterThan", String.class, int.class)
                .getAnnotationMetadata()
                .getAnnotation(DataMethod))
        then:
        values.size() == 2
        values[0] == DataType.STRING
        values[1] == DataType.INTEGER

        when:
        values = getDataTypes(repository.getRequiredMethod("listNativeBooksWithTitleInCollection", Collection.class)
                .getAnnotationMetadata()
                .getAnnotation(DataMethod))
        then:
        values.size() == 1
        values[0] == DataType.STRING

        when:
        values = getDataTypes(repository.getRequiredMethod("listNativeBooksNullableListAsStringArray", List.class)
                .getAnnotationMetadata()
                .getAnnotation(DataMethod))
        then:
        values.size() == 2
        values[0] == DataType.STRING_ARRAY
        values[1] == DataType.STRING_ARRAY
    }

    void "test parameter type entities"() {
        given:
        def repository = buildRepository('test.PersonRepository', '''
import java.util.UUID;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.repository.GenericRepository;

@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    implicitQueries = false,
    namedParameters = false
)
@Repository
@io.micronaut.context.annotation.Executable
interface PersonRepository extends GenericRepository<Person, Long> {
    void deleteAll(Iterable<Person> entities);
}

@MappedEntity
class Person {
    @Id
    protected Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
''')

        expect:

        getDataTypes(repository.getRequiredMethod("deleteAll", Iterable)
            .getAnnotationMetadata()
            .getAnnotation(DataMethod)).length == 0

    }
}
