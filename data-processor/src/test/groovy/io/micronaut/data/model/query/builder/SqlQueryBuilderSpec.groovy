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
package io.micronaut.data.model.query.builder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Join
import io.micronaut.data.model.Association
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.Sort
import io.micronaut.data.model.entities.Bike
import io.micronaut.data.model.entities.MappedEntityCar
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.entities.PersonAssignedId
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.naming.NamingStrategy
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.query.factory.Projections
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.tck.entities.*
import io.micronaut.data.tck.jdbc.entities.Project
import io.micronaut.data.tck.jdbc.entities.UserRole
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

class SqlQueryBuilderSpec extends AbstractTypeElementSpec {

    @Requires({ javaVersion <= 1.8 })
    void 'test configure parameter placeholder format'() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('''
package test;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.query.builder.sql.*;
import java.lang.annotation.*;
import io.micronaut.data.jdbc.annotation.*;
import io.micronaut.context.annotation.*;

@MyAnnotation(dialect = Dialect.POSTGRES)
interface MyRepository {
}

@RepositoryConfiguration(
        queryBuilder = SqlQueryBuilder.class
)
@SqlQueryConfiguration(
    @SqlQueryConfiguration.DialectConfiguration(
        dialect = Dialect.POSTGRES,
        positionalParameterFormat = "$%s",
        escapeQueries = false
    )
)
@Retention(RetentionPolicy.RUNTIME)
@Repository
@interface MyAnnotation {
    @AliasFor(annotation = Repository.class, member = "dialect")
    Dialect dialect() default Dialect.ANSI;
}
''')

        SqlQueryBuilder builder = new SqlQueryBuilder(annotationMetadata)
        PersistentEntity entity = PersistentEntity.of(Sale)
        def queryModel = QueryModel.from(entity).eq("name", QueryParameter.of("name"))

        expect:
        builder.dialect == Dialect.POSTGRES
        builder.buildQuery(queryModel).query == 'SELECT sale_.id,sale_.name,sale_.data,sale_.quantities,sale_.extra_data,sale_.data_list FROM sale sale_ WHERE (sale_.name = $1)'
        builder.buildDelete(queryModel).query == 'DELETE  FROM sale  WHERE (name = $1)'
        builder.buildUpdate(queryModel, Arrays.asList("name")).query == 'UPDATE sale SET name=$1 WHERE (name = $2)'
        builder.buildInsert(annotationMetadata, entity).query == 'INSERT INTO sale (name,data,quantities,extra_data,data_list) VALUES ($1,to_json($2::json),to_json($3::json),to_json($4::json),to_json($5::json))'
    }

    @Requires({ javaVersion >= 1.8 })
    void 'test where annotation replacement'() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('''
package test;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.query.builder.sql.*;
import java.lang.annotation.*;
import io.micronaut.data.jdbc.annotation.*;
import io.micronaut.context.annotation.*;

@MyAnnotation(dialect = Dialect.POSTGRES)
@Where("@.name = :name")
interface MyRepository {
}

@RepositoryConfiguration(
        queryBuilder = SqlQueryBuilder.class
)
@SqlQueryConfiguration(
    @SqlQueryConfiguration.DialectConfiguration(
        dialect = Dialect.POSTGRES,
        positionalParameterFormat = "$%s",
        escapeQueries = false
    )
)
@Retention(RetentionPolicy.RUNTIME)
@Repository
@interface MyAnnotation {
    @AliasFor(annotation = Repository.class, member = "dialect")
    Dialect dialect() default Dialect.ANSI;
}
''')

        SqlQueryBuilder builder = new SqlQueryBuilder(annotationMetadata)
        PersistentEntity entity = PersistentEntity.of(Sale)
        def queryModel = QueryModel.from(entity)

        expect:
        builder.dialect == Dialect.POSTGRES
        builder.buildQuery(annotationMetadata, queryModel).query == 'SELECT sale_.id,sale_.name,sale_.data,sale_.quantities,sale_.extra_data,sale_.data_list FROM sale sale_ WHERE (sale_.name =$1)'
        builder.buildDelete(annotationMetadata, queryModel).query == 'DELETE  FROM sale  WHERE (name =$1)'
        builder.buildUpdate(annotationMetadata, queryModel, Arrays.asList("name")).query == 'UPDATE sale SET name=$1 WHERE (name =$2)'
        builder.buildInsert(annotationMetadata, entity).query == 'INSERT INTO sale (name,data,quantities,extra_data,data_list) VALUES ($1,to_json($2::json),to_json($3::json),to_json($4::json),to_json($5::json))'
    }


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
        PersistentEntity entity = PersistentEntity.of(type)
        QueryModel q = QueryModel.from(entity)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = encoder.buildQuery(q)

        then:"The select includes the schema in the table name reference"
        encoded.query == query

        where:
        type            | query
        Car             | 'SELECT car_.`id`,car_.`name` FROM `ford`.`cars` car_'
        MappedEntityCar | 'SELECT mapped_entity_car_.`id`,mapped_entity_car_.`name` FROM `ford`.`cars` mapped_entity_car_'
    }

    void "test select embedded"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Restaurant)
        QueryModel q = QueryModel.from(entity)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = encoder.buildQuery(q)

        expect:
        encoded.query.startsWith('SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`address_street`,restaurant_.`address_zip_code`,restaurant_.`hqaddress_street`,restaurant_.`hqaddress_zip_code` FROM')

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
        encoded.query == 'SELECT book_.`id`,book_.`author_id`,book_.`genre_id`,book_.`title`,book_.`total_pages`,book_.`publisher_id`,book_.`last_updated`,book_author_.`name` AS author_name,book_author_.`nick_name` AS author_nick_name FROM `book` book_ INNER JOIN `author` book_author_ ON book_.`author_id`=book_author_.`id` WHERE (book_.`id` = ?)'

    }

    void "test encode to-one join - single level, two join entities"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Book)
        QueryModel q = QueryModel.from(entity)
        q.idEq(new QueryParameter("test"))
        q.join(entity.getPropertyByName("author") as Association, Join.Type.FETCH)
        q.join(entity.getPropertyByName("genre") as Association, Join.Type.LEFT_FETCH)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = encoder.buildQuery(q)

        expect:
        encoded.query == 'SELECT book_.`id`,book_.`author_id`,book_.`genre_id`,book_.`title`,book_.`total_pages`,book_.`publisher_id`,book_.`last_updated`,book_genre_.`genre_name` AS genre_genre_name,book_author_.`name` AS author_name,book_author_.`nick_name` AS author_nick_name FROM `book` book_ LEFT JOIN `genre` book_genre_ ON book_.`genre_id`=book_genre_.`id` INNER JOIN `author` book_author_ ON book_.`author_id`=book_author_.`id` WHERE (book_.`id` = ?)'

    }

    void "test encode to-one join - single level, two join entities, outer joins"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Book)
        QueryModel q = QueryModel.from(entity)
        q.idEq(new QueryParameter("test"))
        q.join(entity.getPropertyByName("author") as Association, Join.Type.OUTER)
        q.join(entity.getPropertyByName("genre") as Association, Join.Type.OUTER_FETCH)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.POSTGRES)
        def encoded = encoder.buildQuery(q)

        expect:
        encoded.query == 'SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated",book_genre_."genre_name" AS genre_genre_name FROM "book" book_ FULL OUTER JOIN "genre" book_genre_ ON book_."genre_id"=book_genre_."id" FULL OUTER JOIN "author" book_author_ ON book_."author_id"=book_author_."id" WHERE (book_."id" = ?)'

    }


    void "test encode to-one join - unsupported outer join throws exception for H2 Dialect"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Book)
        QueryModel q = QueryModel.from(entity)
        q.join(entity.getPropertyByName("author") as Association, Join.Type.OUTER)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)

        when:
        encoder.buildQuery(q)

        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER] by dialect [H2]"

    }

    void "test encode to-one join - unsupported outer fetch join throws exception for H2 Dialect"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Book)
        QueryModel q = QueryModel.from(entity)
        q.join(entity.getPropertyByName("author") as Association, Join.Type.OUTER_FETCH)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)

        when:
        encoder.buildQuery(q)

        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER_FETCH] by dialect [H2]"

    }

    void "test encode to-one join - unsupported outer join throws exception for MYSQL Dialect"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Book)
        QueryModel q = QueryModel.from(entity)
        q.join(entity.getPropertyByName("author") as Association, Join.Type.OUTER)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)

        when:
        encoder.buildQuery(q)

        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER] by dialect [MYSQL]"

    }

    void "test encode to-one join - unsupported outer fetch join throws exception for MYSQL Dialect"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Book)
        QueryModel q = QueryModel.from(entity)
        q.join(entity.getPropertyByName("author") as Association, Join.Type.OUTER_FETCH)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)

        when:
        encoder.buildQuery(q)

        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER_FETCH] by dialect [MYSQL]"

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
        result.query == 'INSERT INTO "person" ("name","age","enabled","public_id","company_my_id") VALUES (?,?,?,?,?)'
        result.parameters.equals('1': 'name', '2': 'age', '3': 'enabled', '4': "publicId", '5': 'company.myId')
    }

    void "test encode insert statement for embedded"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Restaurant)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        expect:
        result.query == 'INSERT INTO "restaurant" ("name","address_street","address_zip_code","hqaddress_street","hqaddress_zip_code") VALUES (?,?,?,?,?)'
        result.parameters.equals('1': 'name', '2':'address.street', '3':'address.zipCode', '4':'hqAddress.street', '5':'hqAddress.zipCode')
    }

    void "test encode create statement for embedded"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Restaurant)
        QueryBuilder encoder = new SqlQueryBuilder()
        def result = encoder.buildBatchCreateTableStatement(entity)

        expect:
        result == 'CREATE TABLE "restaurant" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"name" VARCHAR(255) NOT NULL,"address_street" VARCHAR(255) NOT NULL,"address_zip_code" VARCHAR(255) NOT NULL,"hqaddress_street" VARCHAR(255),"hqaddress_zip_code" VARCHAR(255));'
    }

    void "test encode insert statement - custom mapping strategy"() {
        given:
        PersistentEntity entity = getRuntimePersistentEntity(CountryRegion)
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
        result.parameters.equals('1':'name', '2': 'age', '3': 'enabled', '4': 'id')
    }

    void "test encode query with join"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Book)
        SqlQueryBuilder encoder = new SqlQueryBuilder()
        StringBuilder columns = new StringBuilder()
        encoder.selectAllColumns(entity, "book_", columns)

        def query = QueryModel.from(entity)
                .eq("author.nickName", new QueryParameter("test"))
        query.join("author", Join.Type.DEFAULT, null)

        def result = encoder.buildQuery(query)

        expect:
        result.query == "SELECT $columns FROM \"book\" book_ INNER JOIN \"author\" book_author_ ON book_.\"author_id\"=book_author_.\"id\" WHERE (book_author_.\"nick_name\" = ?)"
    }

    @Unroll
    void "test encode query #method - comparison methods"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        QueryModel q = QueryModel.from(entity)
        q."$method"(property, QueryParameter.of('test'))

        SqlQueryBuilder encoder = new SqlQueryBuilder()
        StringBuilder columns = new StringBuilder()
        encoder.selectAllColumns(entity, "person_", columns)
        QueryResult encodedQuery = encoder.buildQuery(q)
        NamingStrategy namingStrategy = NamingStrategies.UnderScoreSeparatedLowerCase.newInstance()
        def mappedName = namingStrategy.mappedName(property)

        expect:
        encodedQuery != null
        mappedName == 'some_id'
        encodedQuery.query ==
                "SELECT $columns FROM \"person\" person_ WHERE (person_.\"${mappedName}\" $operator ?)"
        encodedQuery.parameters == ['1': 'someId']

        where:
        type   | method | property | operator
        Person | 'eq'   | 'someId' | '='
        Person | 'gt'   | 'someId' | '>'
        Person | 'lt'   | 'someId' | '<'
        Person | 'ge'   | 'someId' | '>='
        Person | 'le'   | 'someId' | '<='
        Person | 'like' | 'someId' | 'LIKE'
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
        encodedQuery.parameters == ['1': 'name']

        where:
        type   | method | property | operator | projection
        Person | 'eq'   | 'name'   | '='      | 'max'
        Person | 'gt'   | 'name'   | '>'      | 'min'
        Person | 'lt'   | 'name'   | '<'      | 'sum'
        Person | 'ge'   | 'name'   | '>='     | 'avg'
        Person | 'le'   | 'name'   | '<='     | 'distinct'
    }

    @Unroll
    void "test build query embedded"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            QueryResult encodedQuery = encoder.buildQuery(queryModel)

        then:
            encodedQuery.query == query

        where:
            queryModel << [
                    QueryModel.from(getRuntimePersistentEntity(Shipment)).idEq(new QueryParameter("xyz")),
                    QueryModel.from(getRuntimePersistentEntity(Shipment)).eq("shipmentId.country", new QueryParameter("xyz")),
                    {
                        def entity = getRuntimePersistentEntity(UserRole)
                        def qm = QueryModel.from(entity)
                        qm.join("role", entity.getPropertyByPath("id.role").get() as Association, Join.Type.DEFAULT, null)
                        qm
                    }.call(),
                    {
                        def entity = getRuntimePersistentEntity(UserRole)
                        def qm = QueryModel.from(entity)
                        qm.join("user", entity.getPropertyByPath("id.user").get() as Association, Join.Type.DEFAULT, null)
                        qm.eq("user", new QueryParameter("xyz"))
                    }.call(),
                    QueryModel.from(getRuntimePersistentEntity(UuidEntity)).idEq(new QueryParameter("xyz")),
                    QueryModel.from(getRuntimePersistentEntity(UserRole)).idEq(new QueryParameter("xyz")),
                    {
                        def entity = getRuntimePersistentEntity(Challenge)
                        def qm = QueryModel.from(entity)
                        qm.join("authentication", null, Join.Type.FETCH, null)
                        qm.join("authentication.device", null, Join.Type.FETCH, null)
                        qm.join("authentication.device.user", null, Join.Type.FETCH, null)
                        qm.idEq(new QueryParameter("xyz"))
                        qm
                    }.call(),
                    {
                        def entity = getRuntimePersistentEntity(UserRole)
                        def qm = QueryModel.from(entity)
                        qm.projections().add(Projections.property("role"))
                        qm.join("role", null, Join.Type.FETCH, null)
                        qm.eq("user", new QueryParameter("xyz"))
                        qm
                    }.call(),
                    {
                        def entity = getRuntimePersistentEntity(Meal)
                        def qm = QueryModel.from(entity)
                        qm.join("foods", null, Join.Type.FETCH, null)
                        qm.idEq(new QueryParameter("xyz"))
                        qm
                    }.call()
            ]
            query << [
                    'SELECT shipment_."sp_country",shipment_."sp_city",shipment_."field" FROM "Shipment1" shipment_ WHERE (shipment_."sp_country" = ? AND shipment_."sp_city" = ?)',
                    'SELECT shipment_."sp_country",shipment_."sp_city",shipment_."field" FROM "Shipment1" shipment_ WHERE (shipment_."sp_country" = ?)',
                    'SELECT user_role_."id_user_id",user_role_."id_role_id" FROM "user_role_composite" user_role_ INNER JOIN "role_composite" user_role_id_role_ ON user_role_."id_role_id"=user_role_id_role_."id"',
                    'SELECT user_role_."id_user_id",user_role_."id_role_id" FROM "user_role_composite" user_role_ INNER JOIN "user_composite" user_role_id_user_ ON user_role_."id_user_id"=user_role_id_user_."id" WHERE (user_role_."id_user_id" = ?)',
                    'SELECT uidx."uuid",uidx."name",uidx."child_id",uidx."xyz",uidx."embedded_child_embedded_child2_id",uidx."nullable_value" FROM "uuid_entity" uidx WHERE (uidx."uuid" = ?)',
                    'SELECT user_role_."id_user_id",user_role_."id_role_id" FROM "user_role_composite" user_role_ WHERE (user_role_."id_user_id" = ? AND user_role_."id_role_id" = ?)',
                    'SELECT challenge_."id",challenge_."token",challenge_."authentication_id",challenge_authentication_device_."NAME" AS authentication_device_NAME,challenge_authentication_device_."USER_ID" AS authentication_device_USER_ID,challenge_authentication_device_user_."NAME" AS authentication_device_user_NAME,challenge_authentication_."DESCRIPTION" AS authentication_DESCRIPTION,challenge_authentication_."DEVICE_ID" AS authentication_DEVICE_ID FROM "challenge" challenge_ INNER JOIN "AUTHENTICATION" challenge_authentication_ ON challenge_."authentication_id"=challenge_authentication_."ID" INNER JOIN "DEVICE" challenge_authentication_device_ ON challenge_authentication_."DEVICE_ID"=challenge_authentication_device_."ID" INNER JOIN "USER" challenge_authentication_device_user_ ON challenge_authentication_device_."USER_ID"=challenge_authentication_device_user_."ID" WHERE (challenge_."id" = ?)',
                    'SELECT user_role_id_role_."id",user_role_id_role_."name" FROM "user_role_composite" user_role_ INNER JOIN "role_composite" user_role_id_role_ ON user_role_."id_role_id"=user_role_id_role_."id" WHERE (user_role_."id_user_id" = ?)',
                    'SELECT meal_."mid",meal_."current_blood_glucose",meal_."created_on",meal_."updated_on",meal_."actual",meal_foods_."fid" AS foods_fid,meal_foods_."key" AS foods_key,meal_foods_."carbohydrates" AS foods_carbohydrates,meal_foods_."portion_grams" AS foods_portion_grams,meal_foods_."created_on" AS foods_created_on,meal_foods_."updated_on" AS foods_updated_on,meal_foods_."fk_meal_id" AS foods_fk_meal_id,meal_foods_."fk_alt_meal" AS foods_fk_alt_meal,meal_foods_."loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name" AS ln,meal_foods_."fresh" AS foods_fresh FROM "meal" meal_ INNER JOIN "food" meal_foods_ ON meal_."mid"=meal_foods_."fk_meal_id" WHERE (meal_."mid" = ? AND (meal_.actual = \'Y\' AND meal_foods_.fresh = \'Y\'))'
            ]
    }

    @Unroll
    void "test build insert embedded"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            QueryResult encodedQuery = encoder.buildInsert(entity.getAnnotationMetadata(), entity)

        then:
            encodedQuery.query == query

        where:
            entity << [
                    getRuntimePersistentEntity(Shipment),
                    getRuntimePersistentEntity(UuidEntity),
                    getRuntimePersistentEntity(UserRole)
            ]
            query << [
                    'INSERT INTO "Shipment1" ("field","sp_country","sp_city") VALUES (?,?,?)',
                    'INSERT INTO "uuid_entity" ("name","child_id","xyz","embedded_child_embedded_child2_id","nullable_value","uuid") VALUES (?,?,?,?,?,?)',
                    'INSERT INTO "user_role_composite" ("id_user_id","id_role_id") VALUES (?,?)'
            ]
    }

    @Unroll
    void "test build create embedded"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def statements = encoder.buildCreateTableStatements(entity)

        then:
            statements.join("\n") == query

        where:
            entity << [
                    getRuntimePersistentEntity(Shipment),
                    getRuntimePersistentEntity(UuidEntity),
                    getRuntimePersistentEntity(UserRole)
            ]
            query << [
                    'CREATE TABLE "Shipment1" ("sp_country" VARCHAR(255) NOT NULL,"sp_city" VARCHAR(255) NOT NULL,"field" VARCHAR(255) NOT NULL, PRIMARY KEY("sp_country","sp_city"));',
                    'CREATE TABLE "uuid_entity" ("uuid" UUID,"name" VARCHAR(255) NOT NULL,"child_id" UUID,"xyz" UUID,"embedded_child_embedded_child2_id" UUID,"nullable_value" UUID);',
                    'CREATE TABLE "user_role_composite" ("id_user_id" BIGINT NOT NULL,"id_role_id" BIGINT NOT NULL, PRIMARY KEY("id_user_id","id_role_id"));'
            ]
    }

    void "test build create index from table annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder()
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndex))

        then:
        statements[0] == 'CREATE TABLE "shipment_with_index" ("shipment_id" BIGINT PRIMARY KEY AUTO_INCREMENT,"field" VARCHAR(255) NOT NULL,"taxCode" VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX "idx_shipment_with_index_field_taxcode" ON "shipment_with_index" (field, taxCode);'

        when:
        def productStatements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Product))

        then:
        productStatements.length == 1
        productStatements[0] == 'CREATE TABLE "product" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"name" VARCHAR(255) NOT NULL,"price" DECIMAL NOT NULL,"loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name" VARCHAR(255),"date_created" TIMESTAMP,"last_updated" TIMESTAMP,"category_id" BIGINT);'
    }

    void "test build create index from field annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder()
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnFields))

        then:
        statements[0] == 'CREATE TABLE "shipment_with_index_on_fields" ("shipment_id" BIGINT PRIMARY KEY AUTO_INCREMENT,"field" VARCHAR(255) NOT NULL,"taxCode" VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX "idx_shipment_with_index_on_fields_field" ON "shipment_with_index_on_fields" (field);'
        statements[2] == 'CREATE INDEX "idx_shipment_with_index_on_fields_taxcode" ON "shipment_with_index_on_fields" (taxCode);'
    }

    void "test build create index from field annotation with composite indexes"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder()
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnFieldsCompositeIndexes))

        then:
        statements[0] == 'CREATE TABLE "shipment_with_index_on_fields_composite_indexes" ("shipment_id" BIGINT PRIMARY KEY AUTO_INCREMENT,"field" VARCHAR(255) NOT NULL,"taxCode" VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX "idx_shipment_with_index_on_fields_composite_indexes_field_taxcode" ON "shipment_with_index_on_fields_composite_indexes" (field, taxCode);'
    }

    void "test build create index from index class annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder()
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnClass))

        then:
        statements[0] == 'CREATE TABLE "shipment_with_index_on_class" ("shipment_id" BIGINT PRIMARY KEY AUTO_INCREMENT,"field" VARCHAR(255) NOT NULL,"taxCode" VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX "idx_shipment_with_index_on_class_field" ON "shipment_with_index_on_class" (field);'
        statements[2] == 'CREATE INDEX "idx_shipment_with_index_on_class_taxcode" ON "shipment_with_index_on_class" (taxCode);'
    }

    void "test build create index from index class annotation and field annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder()
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnClassAndFields))

        then:
        statements[0] == 'CREATE TABLE "shipment_with_index_on_class_and_fields" ("shipment_id" BIGINT PRIMARY KEY AUTO_INCREMENT,"field2" VARCHAR(255) NOT NULL,"taxCode2" VARCHAR(255) NOT NULL,"field" VARCHAR(255) NOT NULL,"taxCode" VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX "idx_shipment_with_index_on_class_and_fields_field" ON "shipment_with_index_on_class_and_fields" (field);'
        statements[2] == 'CREATE INDEX "idx_shipment_with_index_on_class_and_fields_taxcode" ON "shipment_with_index_on_class_and_fields" (taxCode);'
        statements[3] == 'CREATE UNIQUE INDEX "idx_shipment_with_index_on_class_and_fields_field2_taxcode2" ON "shipment_with_index_on_class_and_fields" (field2, taxCode2);'
    }

    void "test build composite id query"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def q = encoder.buildQuery(QueryModel.from(getRuntimePersistentEntity(Project)).idEq(new QueryParameter("projectId")))

        then:
            q.query == 'SELECT project_."project_id_department_id",project_."project_id_project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_ WHERE (project_."project_id_department_id" = ? AND project_."project_id_project_id" = ?)'
            q.parameters == [
                    '1': 'projectId.departmentId',
                    '2': 'projectId.projectId'
            ]
    }

    void "test insert statement with version"() {
        given:
            PersistentEntity entity = new RuntimePersistentEntity(Bike)
            QueryBuilder encoder = new SqlQueryBuilder()

        when:
            def insertResult = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity)

        then:
            insertResult.query == 'INSERT INTO "bike" ("name","age","enabled","public_id","version") VALUES (?,?,?,?,?)'
            insertResult.parameters.equals('1': 'name', '2': 'age', '3': "enabled", '4': 'publicId', '5': "version")
    }

    @Shared
    Map<Class, RuntimePersistentEntity> entities = [:]

    // entities have instance compare in some cases
    RuntimePersistentEntity getRuntimePersistentEntity(Class type) {
        RuntimePersistentEntity entity = entities.get(type)
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity getEntity(Class t) {
                    return getRuntimePersistentEntity(t)
                }
            }
            entities.put(type, entity)
        }
        return entity
    }

}
