/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.model.query.builder

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Join
import io.micronaut.data.model.Association
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.Sort
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.entities.PersonAssignedId
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.naming.NamingStrategy
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Car
import io.micronaut.data.tck.entities.City
import io.micronaut.data.tck.entities.CountryRegion
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.entities.Sale
import spock.lang.Specification
import spock.lang.Unroll

class SqlQueryBuilderSpec extends Specification {

    void "test encode update with JSON and MySQL"() {
        when:"A update is encoded"
        PersistentEntity entity = PersistentEntity.of(Sale)
        QueryModel q = QueryModel.from(entity)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def encoded = encoder.buildUpdate(q, ['data'])

        then:"The update query is correct"
        encoded.query == 'UPDATE `sale` SET `data`=CONVERT(? USING UTF8MB4)'
    }

    void "test build queries with schema"() {
        when:"A select is encoded"
        PersistentEntity entity = PersistentEntity.of(Car)
        QueryModel q = QueryModel.from(entity)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = encoder.buildQuery(q)

        then:"The select includes the schema in the table name reference"
        encoded.query == 'SELECT car_.`id`,car_.`name` FROM `ford.cars` car_'
    }

    void "test select embedded"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Restaurant)
        QueryModel q = QueryModel.from(entity)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = encoder.buildQuery(q)

        expect:
        encoded.query.startsWith('SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`address_street`,restaurant_.`address_zip_code` FROM')

    }

    void "test encode to-one join - single level"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Book)
        QueryModel q = QueryModel.from(entity)
        q.idEq(new QueryParameter("test"))
        q.join(entity.getPropertyByName("author") as Association, Join.Type.FETCH)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = encoder.buildQuery(q)

        expect:
        encoded.query == 'SELECT book_.`id`,book_.`author_id`,book_.`title`,book_.`total_pages`,book_.`publisher_id`,book_author_.id AS _author_id,book_author_.name AS _author_name,book_author_.nick_name AS _author_nick_name FROM `book` book_ INNER JOIN author book_author_ ON book_.author_id=book_author_.id WHERE (book_.`id` = ?)'

    }

    void "test encode delete"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(io.micronaut.data.tck.entities.Person)
        QueryModel q = QueryModel.from(entity)
        q.idEq(new QueryParameter("test"))
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        QueryResult encodedQuery = encoder.buildDelete(q)


        expect:
        encodedQuery != null
        encodedQuery.query == "DELETE  FROM `person`  WHERE (`id` = ?)"

    }

    @Unroll
    void "test encode order by #statement"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q.sort Sort.of(props.collect() { Sort.Order."$direction"(it) })

        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        QueryResult encodedQuery = encoder.buildOrderBy(entity, q.getSort())


        expect:
        encodedQuery != null
        encodedQuery.query ==
                " ORDER BY ${statement}"

        where:
        type   | direction | props              | statement
        Person | 'asc'     | ["name"]           | 'person_.name ASC'
        Person | 'asc'     | ["name", "someId"] | 'person_.name ASC,person_.some_id ASC'
        Person | 'desc'    | ["name"]           | 'person_.name DESC'
        Person | 'desc'    | ["name", "someId"] | 'person_.name DESC,person_.some_id DESC'
    }

    void "test encode insert statement"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Person)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO "person" ("name","age","enabled") VALUES (?,?,?)'
        result.parameters.equals(name: '1', age: '2', enabled: '3')
    }

    void "test encode insert statement for embedded"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Restaurant)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO "restaurant" ("name","address_street","address_zip_code") VALUES (?,?,?)'
        result.parameters.equals(name: '1', 'address.street': '2', 'address.zipCode': '3')
    }

    void "test encode create statement for embedded"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Restaurant)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildBatchCreateTableStatement(entity)

        expect:
        result == 'CREATE TABLE "restaurant" ("id" BIGINT AUTO_INCREMENT PRIMARY KEY,"name" VARCHAR(255) NOT NULL,"address_street" VARCHAR(255) NOT NULL,"address_zip_code" VARCHAR(255) NOT NULL);'
    }

    void "test encode insert statement - custom mapping strategy"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(CountryRegion)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO "CountryRegion" ("name","countryId") VALUES (?,?)'
    }

    void "test encode insert statement - custom mapping"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(City)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO "T_CITY" ("C_NAME","country_region_id") VALUES (?,?)'
    }


    void "test encode insert statement - assigned id"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(PersonAssignedId)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO "person_assigned_id" ("name","age","enabled","id") VALUES (?,?,?,?)'
        result.parameters.equals(name: '1', age: '2', enabled: '3', id: '4')
    }

    void "test encode query with join"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Book)
        SqlQueryBuilder encoder = new SqlQueryBuilder()
        def columns = encoder.selectAllColumns(entity, "book_")

        def query = QueryModel.from(entity)
                .eq("author.nickName", new QueryParameter("test"))

        def result = encoder.buildQuery(query)

        expect:
        result.query == "SELECT $columns FROM \"book\" book_ INNER JOIN author book_author_ ON book_.author_id=book_author_.id WHERE (book_author_.\"nick_name\" = ?)"
    }

    @Unroll
    void "test encode query #method - comparison methods"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        SqlQueryBuilder encoder = new SqlQueryBuilder()
        def columns = encoder.selectAllColumns(entity, "person_")
        QueryResult encodedQuery = encoder.buildQuery(q)
        NamingStrategy namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase.newInstance()
        def mappedName = namingStrategy.mappedName(property)

        expect:
        encodedQuery != null
        mappedName == 'some_id'
        encodedQuery.query ==
                "SELECT $columns FROM \"person\" person_ WHERE (person_.\"${mappedName}\" $operator ?)"
        encodedQuery.parameters == ['1': 'test']

        where:
        type   | method | property | operator
        Person | 'eq'   | 'someId' | '='
        Person | 'gt'   | 'someId' | '>'
        Person | 'lt'   | 'someId' | '<'
        Person | 'ge'   | 'someId' | '>='
        Person | 'le'   | 'someId' | '<='
        Person | 'like' | 'someId' | 'like'
        Person | 'ne'   | 'someId' | '!='
    }

    @Unroll
    void "test encode query #method - property projections"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q."$method"(property, QueryParameter.of('test'))
        q.projections()."$projection"(property)
        QueryBuilder encoder = new SqlQueryBuilder()
        QueryResult encodedQuery = encoder.buildQuery(q)
        def aliasName = encoder.getAliasName(entity)

        expect:
        encodedQuery != null
        encodedQuery.query ==
                "SELECT ${projection.toUpperCase()}($aliasName.\"$property\") FROM \"person\" $aliasName WHERE ($aliasName.\"$property\" $operator ?)"
        encodedQuery.parameters == ['1': 'test']

        where:
        type   | method | property | operator | projection
        Person | 'eq'   | 'name'   | '='      | 'max'
        Person | 'gt'   | 'name'   | '>'      | 'min'
        Person | 'lt'   | 'name'   | '<'      | 'sum'
        Person | 'ge'   | 'name'   | '>='     | 'avg'
        Person | 'le'   | 'name'   | '<='     | 'distinct'
    }
}
