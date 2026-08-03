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
import io.micronaut.data.exceptions.MappingException
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.Sort
import io.micronaut.data.model.entities.Bike
import io.micronaut.data.model.entities.GeneratedIdentityOnlyEntity
import io.micronaut.data.model.entities.GeneratedIdOnlyEntity
import io.micronaut.data.model.entities.GeogEntityJson
import io.micronaut.data.model.entities.GeogEntityWkt
import io.micronaut.data.model.entities.GeomEntityCompositeIndex
import io.micronaut.data.model.entities.GeomEntityJson
import io.micronaut.data.model.entities.GeomEntityWGS84
import io.micronaut.data.model.entities.GeomEntityWGS84Wkt
import io.micronaut.data.model.entities.GeomEntityWkt
import io.micronaut.data.model.entities.MappedEntityCar
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.entities.PersonAssignedId
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Car
import io.micronaut.data.tck.entities.City
import io.micronaut.data.tck.entities.CountryRegion
import io.micronaut.data.tck.entities.Owner
import io.micronaut.data.tck.entities.Product
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.entities.Sale
import io.micronaut.data.tck.entities.Shipment
import io.micronaut.data.tck.entities.ShipmentWithIndex
import io.micronaut.data.tck.entities.ShipmentWithIndexOnClass
import io.micronaut.data.tck.entities.ShipmentWithIndexOnClassAndFields
import io.micronaut.data.tck.entities.ShipmentWithIndexOnFields
import io.micronaut.data.tck.entities.ShipmentWithIndexOnFieldsCompositeIndexes
import io.micronaut.data.tck.entities.UuidEntity
import io.micronaut.data.tck.entities.Vehicle
import io.micronaut.data.tck.jdbc.entities.geo.School
import io.micronaut.data.tck.jdbc.entities.Project
import io.micronaut.data.tck.jdbc.entities.UserRole
import jakarta.persistence.criteria.JoinType
import spock.lang.Shared
import spock.lang.Unroll

class SqlQueryBuilderSpec extends AbstractTypeElementSpec {

    @Shared
    RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()

    void 'test configure parameter placeholder format'() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('''
package test;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.query.builder.sql.*;
import java.lang.annotation.*;
import io.micronaut.data.jdbc.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryConfiguration;
import io.micronaut.data.model.query.builder.sql.Dialect;

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

        when:
        SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder(annotationMetadata)

        then:
        sqlQueryBuilder.dialect == Dialect.POSTGRES

        when:
        def query = builder.createQuery()
        def queryRoot = query.from(Sale)
        def result = query.where(builder.equal(queryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        result.query == 'SELECT sale_.id,sale_.name,sale_.data,sale_.quantities,sale_.extra_data,sale_.data_list FROM sale sale_ WHERE (sale_.name = $1)'

        when:
        def deleteQuery = builder.createCriteriaDelete(Sale)
        def deleteQueryRoot = deleteQuery.from(Sale)
        def deleteResult = deleteQuery.where(builder.equal(deleteQueryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        deleteResult.query == 'DELETE  FROM sale  WHERE (name = $1)'

        when:
        def updateQuery = builder.createCriteriaUpdate(Sale)
        def updateQueryRoot = updateQuery.from(Sale)
        updateQuery.set(updateQueryRoot.get('name'), builder.parameter(Object))
        def updateResult = updateQuery.where(builder.equal(updateQueryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        updateResult.query == 'UPDATE sale SET name=$1 WHERE (name = $2)'

        when:
        def insertQuery = builder.createCriteriaInsert(Sale)
        def insertResult = insertQuery.build(sqlQueryBuilder)

        then:
        insertResult.query == 'INSERT INTO sale (name,data,quantities,extra_data,data_list) VALUES ($1,to_json($2::json),to_json($3::json),to_json($4::json),to_json($5::json))'
    }

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

        when:
        SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder(annotationMetadata)

        then:
        sqlQueryBuilder.dialect == Dialect.POSTGRES

        when:
        def query = builder.createQuery()
        def queryRoot = query.from(Sale)
        def result = query.where(builder.equal(queryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        result.query == 'SELECT sale_.id,sale_.name,sale_.data,sale_.quantities,sale_.extra_data,sale_.data_list FROM sale sale_ WHERE (sale_.name = $1)'

        when:
        def deleteQuery = builder.createCriteriaDelete(Sale)
        def deleteQueryRoot = deleteQuery.from(Sale)
        def deleteResult = deleteQuery.where(builder.equal(deleteQueryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        deleteResult.query == 'DELETE  FROM sale  WHERE (name = $1)'

        when:
        def updateQuery = builder.createCriteriaUpdate(Sale)
        def updateQueryRoot = updateQuery.from(Sale)
        updateQuery.set(updateQueryRoot.get('name'), builder.parameter(Object))
        def updateResult = updateQuery.where(builder.equal(updateQueryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        updateResult.query == 'UPDATE sale SET name=$1 WHERE (name = $2)'

        when:
        def insertQuery = builder.createCriteriaInsert(Sale)
        def insertResult = insertQuery.build(sqlQueryBuilder)

        then:
        insertResult.query == 'INSERT INTO sale (name,data,quantities,extra_data,data_list) VALUES ($1,to_json($2::json),to_json($3::json),to_json($4::json),to_json($5::json))'
    }

    void "test encode update with JSON and MySQL"() {
        when:"A update is encoded"
        def query = builder.createCriteriaUpdate(Sale)
        query.set('data', builder.parameter(Object))

        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def encoded = query.build(encoder)

        then:"The update query is correct"
        encoded.query == 'UPDATE `sale` SET `data`=CONVERT(? USING UTF8MB4)'
    }

    void "test build queries with schema"() {
        when:"A select is encoded"
        def criteriaQuery = builder.createQuery(type)

        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = criteriaQuery.build(encoder)

        then:"The select includes the schema in the table name reference"
        encoded.query == query

        where:
        type            | query
        Car             | 'SELECT car_.`id`,car_.`name` FROM `ford`.`cars` car_'
        MappedEntityCar | 'SELECT mapped_entity_car_.`id`,mapped_entity_car_.`name` FROM `ford`.`cars` mapped_entity_car_'
    }

    void "test select embedded"() {
        given:
        def criteriaQuery = builder.createQuery(Restaurant)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.H2)
        def encoded = criteriaQuery.build(encoder)

        expect:
        encoded.query.startsWith('SELECT restaurant_.`id`,restaurant_.`name`,restaurant_.`street`,restaurant_.`zip_code`,restaurant_.`hqaddress_street`,restaurant_.`hqaddress_zip_code` FROM')
    }

    void "test h2 crud"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('''
package test;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.query.builder.sql.*;
import java.lang.annotation.*;
import io.micronaut.data.jdbc.annotation.*;
import io.micronaut.context.annotation.*;

@MyAnnotation(dialect = Dialect.H2)
interface MyRepository {
}

@RepositoryConfiguration(
        queryBuilder = SqlQueryBuilder.class
)
@SqlQueryConfiguration(
    @SqlQueryConfiguration.DialectConfiguration(
        dialect = Dialect.H2,
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

        when:
        SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder(annotationMetadata)

        then:
        sqlQueryBuilder.dialect == Dialect.H2

        when:
        def query = builder.createQuery()
        def queryRoot = query.from(Sale)
        def result = query.where(builder.equal(queryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        result.query == 'SELECT sale_.id,sale_.name,sale_.data,sale_.quantities,sale_.extra_data,sale_.data_list FROM sale sale_ WHERE (sale_.name = $1)'

        when:
        def deleteQuery = builder.createCriteriaDelete(Sale)
        def deleteQueryRoot = deleteQuery.from(Sale)
        def deleteResult = deleteQuery.where(builder.equal(deleteQueryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        deleteResult.query == 'DELETE  FROM sale  WHERE (name = $1)'

        when:
        def updateQuery = builder.createCriteriaUpdate(Sale)
        def updateQueryRoot = updateQuery.from(Sale)
        updateQuery.set(updateQueryRoot.get('name'), builder.parameter(Object))
        def updateResult = updateQuery.where(builder.equal(updateQueryRoot.get("name"), builder.parameter(Object))).build(sqlQueryBuilder)

        then:
        updateResult.query == 'UPDATE sale SET name=$1 WHERE (name = $2)'

        when:
        def insertQuery = builder.createCriteriaInsert(Sale)
        def insertResult = insertQuery.build(sqlQueryBuilder)

        then:
        insertResult.query == 'INSERT INTO sale (name,data,quantities,extra_data,data_list) VALUES ($1,$2 FORMAT JSON,$3 FORMAT JSON,$4 FORMAT JSON,$5 FORMAT JSON)'
    }

    void "test encode to-one join - single level"() {
        given:
        def query = builder.createQuery()
        def root = query.from(Book)
        root.fetch("author")
        def encoded = query.where(
                builder.equal(root.id(), builder.parameter(Object))
        ).build(new SqlQueryBuilder(Dialect.H2))

        expect:
        encoded.query == 'SELECT book_.`id`,book_.`author_id`,book_.`genre_id`,book_.`title`,book_.`total_pages`,book_.`publisher_id`,book_.`last_updated`,book_author_.`name` AS author_name,book_author_.`nick_name` AS author_nick_name FROM `book` book_ INNER JOIN `author` book_author_ ON book_.`author_id`=book_author_.`id` WHERE (book_.`id` = ?)'
    }

    void "test encode to-one join - single level, two join entities"() {
        given:
        def query = builder.createQuery()
        def root = query.from(Book)
        root.fetch("author")
        root.fetch("genre", JoinType.LEFT)
        def encoded = query.where(
                builder.equal(root.id(), builder.parameter(Object))
        ).build(new SqlQueryBuilder(Dialect.H2))

        expect:
        encoded.query == 'SELECT book_.`id`,book_.`author_id`,book_.`genre_id`,book_.`title`,book_.`total_pages`,book_.`publisher_id`,book_.`last_updated`,book_author_.`name` AS author_name,book_author_.`nick_name` AS author_nick_name,book_genre_.`genre_name` AS genre_genre_name FROM `book` book_ LEFT JOIN `genre` book_genre_ ON book_.`genre_id`=book_genre_.`id` INNER JOIN `author` book_author_ ON book_.`author_id`=book_author_.`id` WHERE (book_.`id` = ?)'

    }

    void "test encode to-one join - single level, two join entities, outer joins"() {
        given:
        def query = builder.createQuery()
        def root = query.from(Book)
        root.join("author", Join.Type.OUTER)
        root.join("genre", Join.Type.OUTER_FETCH)
        def encoded = query.where(
                builder.equal(root.id(), builder.parameter(Object))
        ).build(new SqlQueryBuilder(Dialect.POSTGRES))

        expect:
        encoded.query == 'SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated",book_genre_."genre_name" AS genre_genre_name FROM "book" book_ FULL OUTER JOIN "genre" book_genre_ ON book_."genre_id"=book_genre_."id" FULL OUTER JOIN "author" book_author_ ON book_."author_id"=book_author_."id" WHERE (book_."id" = ?)'
    }

    void "test encode to-one join - unsupported outer join throws exception for H2 Dialect"() {
        given:
        def query = builder.createQuery()
        def root = query.from(Book)
        root.join("author", Join.Type.OUTER)

        when:
            query.where(
                    builder.equal(root.id(), builder.parameter(Object))
            ).build(new SqlQueryBuilder(Dialect.H2))
        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER] by dialect [H2]"

    }

    void "test encode to-one join - unsupported outer fetch join throws exception for H2 Dialect"() {
        given:
        def query = builder.createQuery()
        def root = query.from(Book)
        root.join("author", Join.Type.OUTER_FETCH)

        when:
        query.where(
                builder.equal(root.id(), builder.parameter(Object))
        ).build(new SqlQueryBuilder(Dialect.H2))

        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER_FETCH] by dialect [H2]"
    }

    void "test encode to-one join - unsupported outer join throws exception for MYSQL Dialect"() {
        given:
        def query = builder.createQuery()
        def root = query.from(Book)
        root.join("author", Join.Type.OUTER)

        when:
        query.where(
                builder.equal(root.id(), builder.parameter(Object))
        ).build(new SqlQueryBuilder(Dialect.MYSQL))

        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER] by dialect [MYSQL]"

    }

    void "test encode to-one join - unsupported outer fetch join throws exception for MYSQL Dialect"() {
        given:
        def query = builder.createQuery()
        def root = query.from(Book)
        root.join("author", Join.Type.OUTER_FETCH)

        when:
        query.where(
                builder.equal(root.id(), builder.parameter(Object))
        ).build(new SqlQueryBuilder(Dialect.MYSQL))

        then:
        def e = thrown(IllegalArgumentException)

        expect:
        e.message == "Unsupported join type [OUTER_FETCH] by dialect [MYSQL]"

    }

    void "test encode delete"() {
        given:
        def deleteQuery = builder.createCriteriaDelete(io.micronaut.data.tck.entities.Person)
        PersistentEntity entity = new RuntimePersistentEntity(io.micronaut.data.tck.entities.Person)
        QueryResult encodedQuery = deleteQuery.where(
                builder.equal(deleteQuery.root.get("id"), builder.parameter(Object))
        ).build(new SqlQueryBuilder(Dialect.H2))

        expect:
        encodedQuery != null
        encodedQuery.query == "DELETE  FROM `person`  WHERE (`id` = ?)"
    }

    @Unroll
    void "test encode order by #statement"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(type)
        Sort sort = Sort.of(props.collect() { Sort.Order."$direction"(it)})

        String query = new SqlQueryBuilder(Dialect.H2).buildOrderBy("", entity, AnnotationMetadata.EMPTY_METADATA, sort, false, null)

        expect:
        query == " ORDER BY ${statement}"

        where:
        type   | direction | props              | statement
        Person | 'asc'     | ["name"]           | 'person_.`name` ASC'
        Person | 'asc'     | ["name", "someId"] | 'person_.`name` ASC,person_.`some_id` ASC'
        Person | 'desc'    | ["name"]           | 'person_.`name` DESC'
        Person | 'desc'    | ["name", "someId"] | 'person_.`name` DESC,person_.`some_id` DESC'
    }

    void 'test encode order by uppercase mapped column'() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Owner)
        Sort sort = Sort.of(Sort.Order.asc('name'))

        when:
        String query = new SqlQueryBuilder(Dialect.POSTGRES).buildOrderBy('', entity, AnnotationMetadata.EMPTY_METADATA, sort, false, null)

        then:
        query == ' ORDER BY owner_."OWNER_NAME" ASC'
    }

    void "test encode insert statement"() {
        given:
        def result = builder.createCriteriaInsert(Person).build(new SqlQueryBuilder())

        expect:
        result.query == 'INSERT INTO "person" ("name","age","enabled","public_id","company_id") VALUES (?,?,?,?,?)'
        result.parameters.equals('1': 'name', '2': 'age', '3': 'enabled', '4': "publicId", '5': 'company.myId')
    }

    @Unroll
    void "test encode #dialect insert statement with only generated id"() {
        given:
        def result = builder.createCriteriaInsert(GeneratedIdOnlyEntity).build(new SqlQueryBuilder(dialect))

        expect:
        result.query == expectedQuery
        result.parameters.isEmpty()

        where:
        dialect          || expectedQuery
        Dialect.ANSI     || 'INSERT INTO "generated_id_only_entity" DEFAULT VALUES'
        Dialect.H2       || 'INSERT INTO `generated_id_only_entity` DEFAULT VALUES'
        Dialect.MYSQL    || 'INSERT INTO `generated_id_only_entity` () VALUES ()'
        Dialect.POSTGRES || 'INSERT INTO "generated_id_only_entity" DEFAULT VALUES'
        Dialect.SQL_SERVER || 'INSERT INTO [generated_id_only_entity] DEFAULT VALUES'
        Dialect.ORACLE   || 'INSERT INTO "GENERATED_ID_ONLY_ENTITY" ("ID") VALUES ("GENERATED_ID_ONLY_ENTITY_SEQ".nextval)'
    }

    void "test encode oracle insert statement with only identity id"() {
        given:
        def result = builder.createCriteriaInsert(GeneratedIdentityOnlyEntity).build(new SqlQueryBuilder(Dialect.ORACLE))

        expect:
        result.query == 'INSERT INTO "GENERATED_IDENTITY_ONLY_ENTITY" VALUES (DEFAULT)'
        result.parameters.isEmpty()
    }

    void "test encode insert statement for embedded"() {
        given:
        def result = builder.createCriteriaInsert(Restaurant).build(new SqlQueryBuilder())

        expect:
        result.query == 'INSERT INTO "restaurant" ("name","street","zip_code","hqaddress_street","hqaddress_zip_code") VALUES (?,?,?,?,?)'
        result.parameters.equals('1': 'name', '2':'address.street', '3':'address.zipCode', '4':'hqAddress.street', '5':'hqAddress.zipCode')
    }

    @Unroll
    void "test encode #dialect insert statement for geospatial properties for #entityClass.simpleName"() {
        given:
        def result = builder.createCriteriaInsert(entityClass).build(new SqlQueryBuilder(dialect))

        expect:
        result.query == expectedQuery

        where:
        dialect            | entityClass    || expectedQuery
        Dialect.ORACLE     | GeomEntityJson || 'INSERT INTO "GEOM_ENTITY_JSON" ("LOCATION","MULTI_POINT","LINE_STRING","MULTI_LINE_STRING","ID") VALUES (SDO_UTIL.FROM_GEOJSON(?, NULL, 3857),SDO_UTIL.FROM_GEOJSON(?),SDO_UTIL.FROM_GEOJSON(?, NULL, 3857),SDO_UTIL.FROM_GEOJSON(?),"GEOM_ENTITY_JSON_SEQ".nextval)'
        Dialect.ORACLE     | GeomEntityWkt  || 'INSERT INTO "GEOM_ENTITY_WKT" ("LOCATION","MULTI_POINT","LINE_STRING","MULTI_LINE_STRING","ID") VALUES (SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?), 3857),SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?)),SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?), 3857),SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?)),"GEOM_ENTITY_WKT_SEQ".nextval)'
        Dialect.MYSQL      | GeomEntityJson || 'INSERT INTO `geom_entity_json` (`location`,`multi_point`,`line_string`,`multi_line_string`) VALUES (ST_GeomFromGeoJSON(?, 1, 3857),ST_GeomFromGeoJSON(?),ST_GeomFromGeoJSON(?, 1, 3857),ST_GeomFromGeoJSON(?))'
        Dialect.MYSQL      | GeomEntityWkt  || 'INSERT INTO `geom_entity_wkt` (`location`,`multi_point`,`line_string`,`multi_line_string`) VALUES (ST_GeomFromText(?, 3857),ST_GeomFromText(?),ST_GeomFromText(?, 3857),ST_GeomFromText(?))'
        Dialect.H2         | GeomEntityJson || 'INSERT INTO `geom_entity_json` (`location`,`multi_point`,`line_string`,`multi_line_string`) VALUES (ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),ST_GeomFromGeoJSON(?),ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),ST_GeomFromGeoJSON(?))'
        Dialect.H2         | GeomEntityWkt  || 'INSERT INTO `geom_entity_wkt` (`location`,`multi_point`,`line_string`,`multi_line_string`) VALUES (ST_GeomFromText(?, 3857),ST_GeomFromText(?),ST_GeomFromText(?, 3857),ST_GeomFromText(?))'
        Dialect.POSTGRES   | GeomEntityJson || 'INSERT INTO "geom_entity_json" ("location","multi_point","line_string","multi_line_string") VALUES (ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),ST_GeomFromGeoJSON(?),ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),ST_GeomFromGeoJSON(?))'
        Dialect.POSTGRES   | GeomEntityWkt  || 'INSERT INTO "geom_entity_wkt" ("location","multi_point","line_string","multi_line_string") VALUES (ST_GeomFromText(?, 3857),ST_GeomFromText(?),ST_GeomFromText(?, 3857),ST_GeomFromText(?))'
        Dialect.POSTGRES   | GeogEntityJson || 'INSERT INTO "geog_entity_json" ("location","multi_point","line_string","multi_line_string") VALUES (ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)::geography,ST_GeomFromGeoJSON(?)::geography,ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)::geography,ST_GeomFromGeoJSON(?)::geography)'
        Dialect.POSTGRES   | GeogEntityWkt  || 'INSERT INTO "geog_entity_wkt" ("location","multi_point","line_string","multi_line_string") VALUES (ST_GeomFromText(?, 3857)::geography,ST_GeomFromText(?)::geography,ST_GeomFromText(?, 3857)::geography,ST_GeomFromText(?)::geography)'
        Dialect.SQL_SERVER | GeomEntityJson || 'INSERT INTO [geom_entity_json] ([location],[multi_point],[line_string],[multi_line_string]) VALUES (geometry::STGeomFromText(?, 3857),geometry::STGeomFromText(?, 3857),geometry::STGeomFromText(?, 3857),geometry::STGeomFromText(?, 3857))'
        Dialect.SQL_SERVER | GeomEntityWkt  || 'INSERT INTO [geom_entity_wkt] ([location],[multi_point],[line_string],[multi_line_string]) VALUES (geometry::STGeomFromText(?, 3857),geometry::STGeomFromText(?, 3857),geometry::STGeomFromText(?, 3857),geometry::STGeomFromText(?, 3857))'
        Dialect.SQL_SERVER | GeogEntityJson || 'INSERT INTO [geog_entity_json] ([location],[multi_point],[line_string],[multi_line_string]) VALUES (geography::STGeomFromText(?, 3857),geography::STGeomFromText(?, 4326),geography::STGeomFromText(?, 3857),geography::STGeomFromText(?, 4326))'
        Dialect.SQL_SERVER | GeogEntityWkt  || 'INSERT INTO [geog_entity_wkt] ([location],[multi_point],[line_string],[multi_line_string]) VALUES (geography::STGeomFromText(?, 3857),geography::STGeomFromText(?, 4326),geography::STGeomFromText(?, 3857),geography::STGeomFromText(?, 4326))'
    }

    @Unroll
    void "test encode #dialect update statement for geospatial properties for #entityClass.simpleName"() {
        given:
        def query = builder.createCriteriaUpdate(entityClass)
        def root = query.from(entityClass)
        query.set(root.get('point'), builder.parameter(Object))
        query.set(root.get('multiPoint'), builder.parameter(Object))
        query.set(root.get('lineString'), builder.parameter(Object))
        query.set(root.get('multiLineString'), builder.parameter(Object))
        query.where(builder.equal(root.get('id'), builder.parameter(Object)))
        def result = query.build(new SqlQueryBuilder(dialect))

        expect:
        result.query == expectedQuery

        where:
        dialect            | entityClass    || expectedQuery
        Dialect.ORACLE     | GeomEntityJson || 'UPDATE "GEOM_ENTITY_JSON" SET "LOCATION"=SDO_UTIL.FROM_GEOJSON(?, NULL, 3857),"MULTI_POINT"=SDO_UTIL.FROM_GEOJSON(?),"LINE_STRING"=SDO_UTIL.FROM_GEOJSON(?, NULL, 3857),"MULTI_LINE_STRING"=SDO_UTIL.FROM_GEOJSON(?) WHERE ("ID" = ?)'
        Dialect.ORACLE     | GeomEntityWkt  || 'UPDATE "GEOM_ENTITY_WKT" SET "LOCATION"=SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?), 3857),"MULTI_POINT"=SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?)),"LINE_STRING"=SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?), 3857),"MULTI_LINE_STRING"=SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?)) WHERE ("ID" = ?)'
        Dialect.MYSQL      | GeomEntityJson || 'UPDATE `geom_entity_json` SET `location`=ST_GeomFromGeoJSON(?, 1, 3857),`multi_point`=ST_GeomFromGeoJSON(?),`line_string`=ST_GeomFromGeoJSON(?, 1, 3857),`multi_line_string`=ST_GeomFromGeoJSON(?) WHERE (`id` = ?)'
        Dialect.MYSQL      | GeomEntityWkt  || 'UPDATE `geom_entity_wkt` SET `location`=ST_GeomFromText(?, 3857),`multi_point`=ST_GeomFromText(?),`line_string`=ST_GeomFromText(?, 3857),`multi_line_string`=ST_GeomFromText(?) WHERE (`id` = ?)'
        Dialect.H2         | GeomEntityJson || 'UPDATE `geom_entity_json` SET `location`=ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),`multi_point`=ST_GeomFromGeoJSON(?),`line_string`=ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),`multi_line_string`=ST_GeomFromGeoJSON(?) WHERE (`id` = ?)'
        Dialect.H2         | GeomEntityWkt  || 'UPDATE `geom_entity_wkt` SET `location`=ST_GeomFromText(?, 3857),`multi_point`=ST_GeomFromText(?),`line_string`=ST_GeomFromText(?, 3857),`multi_line_string`=ST_GeomFromText(?) WHERE (`id` = ?)'
        Dialect.POSTGRES   | GeomEntityJson || 'UPDATE "geom_entity_json" SET "location"=ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),"multi_point"=ST_GeomFromGeoJSON(?),"line_string"=ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),"multi_line_string"=ST_GeomFromGeoJSON(?) WHERE ("id" = ?)'
        Dialect.POSTGRES   | GeomEntityWkt  || 'UPDATE "geom_entity_wkt" SET "location"=ST_GeomFromText(?, 3857),"multi_point"=ST_GeomFromText(?),"line_string"=ST_GeomFromText(?, 3857),"multi_line_string"=ST_GeomFromText(?) WHERE ("id" = ?)'
        Dialect.POSTGRES   | GeogEntityJson || 'UPDATE "geog_entity_json" SET "location"=ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)::geography,"multi_point"=ST_GeomFromGeoJSON(?)::geography,"line_string"=ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)::geography,"multi_line_string"=ST_GeomFromGeoJSON(?)::geography WHERE ("id" = ?)'
        Dialect.POSTGRES   | GeogEntityWkt  || 'UPDATE "geog_entity_wkt" SET "location"=ST_GeomFromText(?, 3857)::geography,"multi_point"=ST_GeomFromText(?)::geography,"line_string"=ST_GeomFromText(?, 3857)::geography,"multi_line_string"=ST_GeomFromText(?)::geography WHERE ("id" = ?)'
        Dialect.SQL_SERVER | GeomEntityJson || 'UPDATE [geom_entity_json] SET [location]=geometry::STGeomFromText(?, 3857),[multi_point]=geometry::STGeomFromText(?, 3857),[line_string]=geometry::STGeomFromText(?, 3857),[multi_line_string]=geometry::STGeomFromText(?, 3857) WHERE ([id] = ?)'
        Dialect.SQL_SERVER | GeomEntityWkt  || 'UPDATE [geom_entity_wkt] SET [location]=geometry::STGeomFromText(?, 3857),[multi_point]=geometry::STGeomFromText(?, 3857),[line_string]=geometry::STGeomFromText(?, 3857),[multi_line_string]=geometry::STGeomFromText(?, 3857) WHERE ([id] = ?)'
        Dialect.SQL_SERVER | GeogEntityJson || 'UPDATE [geog_entity_json] SET [location]=geography::STGeomFromText(?, 3857),[multi_point]=geography::STGeomFromText(?, 4326),[line_string]=geography::STGeomFromText(?, 3857),[multi_line_string]=geography::STGeomFromText(?, 4326) WHERE ([id] = ?)'
        Dialect.SQL_SERVER | GeogEntityWkt  || 'UPDATE [geog_entity_wkt] SET [location]=geography::STGeomFromText(?, 3857),[multi_point]=geography::STGeomFromText(?, 4326),[line_string]=geography::STGeomFromText(?, 3857),[multi_line_string]=geography::STGeomFromText(?, 4326) WHERE ([id] = ?)'
    }

    @Unroll
    void "test encode #dialect read statement for geospatial properties for #entityClass.simpleName"() {
        given:
        def query = builder.createQuery(entityClass)
        def root = query.from(entityClass)
        query.where(builder.equal(root.get('id'), builder.parameter(Object)))
        def result = query.build(new SqlQueryBuilder(dialect))

        expect:
        result.query == expectedQuery

        where:
        dialect            | entityClass   || expectedQuery
        Dialect.ORACLE     | GeomEntityJson || 'SELECT geom_entity_json_."ID",SDO_UTIL.TO_GEOJSON(geom_entity_json_."LOCATION") AS "LOCATION",SDO_UTIL.TO_GEOJSON(geom_entity_json_."MULTI_POINT") AS "MULTI_POINT",SDO_UTIL.TO_GEOJSON(geom_entity_json_."LINE_STRING") AS "LINE_STRING",SDO_UTIL.TO_GEOJSON(geom_entity_json_."MULTI_LINE_STRING") AS "MULTI_LINE_STRING" FROM "GEOM_ENTITY_JSON" geom_entity_json_ WHERE (geom_entity_json_."ID" = ?)'
        Dialect.ORACLE     | GeomEntityWkt  || 'SELECT geom_entity_wkt_."ID",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_."LOCATION") AS "LOCATION",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_."MULTI_POINT") AS "MULTI_POINT",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_."LINE_STRING") AS "LINE_STRING",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_."MULTI_LINE_STRING") AS "MULTI_LINE_STRING" FROM "GEOM_ENTITY_WKT" geom_entity_wkt_ WHERE (geom_entity_wkt_."ID" = ?)'
        Dialect.MYSQL      | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (geom_entity_json_.`id` = ?)'
        Dialect.MYSQL      | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (geom_entity_wkt_.`id` = ?)'
        Dialect.H2         | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (geom_entity_json_.`id` = ?)'
        Dialect.H2         | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (geom_entity_wkt_.`id` = ?)'
        Dialect.POSTGRES   | GeomEntityJson || 'SELECT geom_entity_json_."id",ST_AsGeoJSON(geom_entity_json_."location") AS "location",ST_AsGeoJSON(geom_entity_json_."multi_point") AS "multi_point",ST_AsGeoJSON(geom_entity_json_."line_string") AS "line_string",ST_AsGeoJSON(geom_entity_json_."multi_line_string") AS "multi_line_string" FROM "geom_entity_json" geom_entity_json_ WHERE (geom_entity_json_."id" = ?)'
        Dialect.POSTGRES   | GeomEntityWkt  || 'SELECT geom_entity_wkt_."id",ST_AsText(geom_entity_wkt_."location") AS "location",ST_AsText(geom_entity_wkt_."multi_point") AS "multi_point",ST_AsText(geom_entity_wkt_."line_string") AS "line_string",ST_AsText(geom_entity_wkt_."multi_line_string") AS "multi_line_string" FROM "geom_entity_wkt" geom_entity_wkt_ WHERE (geom_entity_wkt_."id" = ?)'
        Dialect.POSTGRES   | GeogEntityJson || 'SELECT geog_entity_json_."id",ST_AsGeoJSON(geog_entity_json_."location"::geometry) AS "location",ST_AsGeoJSON(geog_entity_json_."multi_point"::geometry) AS "multi_point",ST_AsGeoJSON(geog_entity_json_."line_string"::geometry) AS "line_string",ST_AsGeoJSON(geog_entity_json_."multi_line_string"::geometry) AS "multi_line_string" FROM "geog_entity_json" geog_entity_json_ WHERE (geog_entity_json_."id" = ?)'
        Dialect.POSTGRES   | GeogEntityWkt  || 'SELECT geog_entity_wkt_."id",ST_AsText(geog_entity_wkt_."location"::geometry) AS "location",ST_AsText(geog_entity_wkt_."multi_point"::geometry) AS "multi_point",ST_AsText(geog_entity_wkt_."line_string"::geometry) AS "line_string",ST_AsText(geog_entity_wkt_."multi_line_string"::geometry) AS "multi_line_string" FROM "geog_entity_wkt" geog_entity_wkt_ WHERE (geog_entity_wkt_."id" = ?)'
        Dialect.SQL_SERVER | GeomEntityJson || 'SELECT geom_entity_json_.[id],geom_entity_json_.[location].STAsText() AS [location],geom_entity_json_.[multi_point].STAsText() AS [multi_point],geom_entity_json_.[line_string].STAsText() AS [line_string],geom_entity_json_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_json] geom_entity_json_ WHERE (geom_entity_json_.[id] = ?)'
        Dialect.SQL_SERVER | GeomEntityWkt  || 'SELECT geom_entity_wkt_.[id],geom_entity_wkt_.[location].STAsText() AS [location],geom_entity_wkt_.[multi_point].STAsText() AS [multi_point],geom_entity_wkt_.[line_string].STAsText() AS [line_string],geom_entity_wkt_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_wkt] geom_entity_wkt_ WHERE (geom_entity_wkt_.[id] = ?)'
        Dialect.SQL_SERVER | GeogEntityJson || 'SELECT geog_entity_json_.[id],geog_entity_json_.[location].STAsText() AS [location],geog_entity_json_.[multi_point].STAsText() AS [multi_point],geog_entity_json_.[line_string].STAsText() AS [line_string],geog_entity_json_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geog_entity_json] geog_entity_json_ WHERE (geog_entity_json_.[id] = ?)'
        Dialect.SQL_SERVER | GeogEntityWkt  || 'SELECT geog_entity_wkt_.[id],geog_entity_wkt_.[location].STAsText() AS [location],geog_entity_wkt_.[multi_point].STAsText() AS [multi_point],geog_entity_wkt_.[line_string].STAsText() AS [line_string],geog_entity_wkt_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geog_entity_wkt] geog_entity_wkt_ WHERE (geog_entity_wkt_.[id] = ?)'
    }

    @Unroll
    void "test encode #dialect geoWithin predicate for #entityClass.simpleName"() {
        given:
        def query = builder.createQuery(entityClass)
        def root = query.from(entityClass)
        query.where(builder.geoWithin(root.get('point'), builder.parameter(Object)))

        expect:
        query.build(new SqlQueryBuilder(dialect)).query == expectedQuery

        where:
        dialect            | entityClass    || expectedQuery
        Dialect.ORACLE     | GeomEntityJson || "SELECT geom_entity_json_.\"ID\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"LOCATION\") AS \"LOCATION\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"MULTI_POINT\") AS \"MULTI_POINT\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"LINE_STRING\") AS \"LINE_STRING\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"MULTI_LINE_STRING\") AS \"MULTI_LINE_STRING\" FROM \"GEOM_ENTITY_JSON\" geom_entity_json_ WHERE (SDO_INSIDE(geom_entity_json_.\"LOCATION\",SDO_UTIL.FROM_GEOJSON(?, NULL, 3857)) = 'TRUE')"
        Dialect.ORACLE     | GeomEntityWkt  || "SELECT geom_entity_wkt_.\"ID\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"LOCATION\") AS \"LOCATION\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"MULTI_POINT\") AS \"MULTI_POINT\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"LINE_STRING\") AS \"LINE_STRING\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"MULTI_LINE_STRING\") AS \"MULTI_LINE_STRING\" FROM \"GEOM_ENTITY_WKT\" geom_entity_wkt_ WHERE (SDO_INSIDE(geom_entity_wkt_.\"LOCATION\",SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?), 3857)) = 'TRUE')"
        Dialect.MYSQL      | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (ST_Within(geom_entity_json_.`location`,ST_GeomFromGeoJSON(?, 1, 3857)))'
        Dialect.MYSQL      | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (ST_Within(geom_entity_wkt_.`location`,ST_GeomFromText(?, 3857)))'
        Dialect.H2         | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (ST_Within(geom_entity_json_.`location`,ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)))'
        Dialect.H2         | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (ST_Within(geom_entity_wkt_.`location`,ST_GeomFromText(?, 3857)))'
        Dialect.POSTGRES   | GeomEntityJson || 'SELECT geom_entity_json_."id",ST_AsGeoJSON(geom_entity_json_."location") AS "location",ST_AsGeoJSON(geom_entity_json_."multi_point") AS "multi_point",ST_AsGeoJSON(geom_entity_json_."line_string") AS "line_string",ST_AsGeoJSON(geom_entity_json_."multi_line_string") AS "multi_line_string" FROM "geom_entity_json" geom_entity_json_ WHERE (ST_Within(geom_entity_json_."location",ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)))'
        Dialect.POSTGRES   | GeomEntityWkt  || 'SELECT geom_entity_wkt_."id",ST_AsText(geom_entity_wkt_."location") AS "location",ST_AsText(geom_entity_wkt_."multi_point") AS "multi_point",ST_AsText(geom_entity_wkt_."line_string") AS "line_string",ST_AsText(geom_entity_wkt_."multi_line_string") AS "multi_line_string" FROM "geom_entity_wkt" geom_entity_wkt_ WHERE (ST_Within(geom_entity_wkt_."location",ST_GeomFromText(?, 3857)))'
        Dialect.SQL_SERVER | GeomEntityJson || 'SELECT geom_entity_json_.[id],geom_entity_json_.[location].STAsText() AS [location],geom_entity_json_.[multi_point].STAsText() AS [multi_point],geom_entity_json_.[line_string].STAsText() AS [line_string],geom_entity_json_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_json] geom_entity_json_ WHERE (geom_entity_json_.[location].STWithin(geometry::STGeomFromText(?, 3857)) = 1)'
        Dialect.SQL_SERVER | GeomEntityWkt  || 'SELECT geom_entity_wkt_.[id],geom_entity_wkt_.[location].STAsText() AS [location],geom_entity_wkt_.[multi_point].STAsText() AS [multi_point],geom_entity_wkt_.[line_string].STAsText() AS [line_string],geom_entity_wkt_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_wkt] geom_entity_wkt_ WHERE (geom_entity_wkt_.[location].STWithin(geometry::STGeomFromText(?, 3857)) = 1)'
    }

    @Unroll
    void "test encode #dialect geoIntersects predicate for #entityClass.simpleName"() {
        given:
        def query = builder.createQuery(entityClass)
        def root = query.from(entityClass)
        query.where(builder.geoIntersects(root.get('point'), builder.parameter(Object)))

        expect:
        query.build(new SqlQueryBuilder(dialect)).query == expectedQuery

        where:
        dialect            | entityClass    || expectedQuery
        Dialect.ORACLE     | GeomEntityJson || "SELECT geom_entity_json_.\"ID\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"LOCATION\") AS \"LOCATION\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"MULTI_POINT\") AS \"MULTI_POINT\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"LINE_STRING\") AS \"LINE_STRING\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"MULTI_LINE_STRING\") AS \"MULTI_LINE_STRING\" FROM \"GEOM_ENTITY_JSON\" geom_entity_json_ WHERE (SDO_ANYINTERACT(geom_entity_json_.\"LOCATION\",SDO_UTIL.FROM_GEOJSON(?, NULL, 3857)) = 'TRUE')"
        Dialect.ORACLE     | GeomEntityWkt  || "SELECT geom_entity_wkt_.\"ID\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"LOCATION\") AS \"LOCATION\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"MULTI_POINT\") AS \"MULTI_POINT\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"LINE_STRING\") AS \"LINE_STRING\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"MULTI_LINE_STRING\") AS \"MULTI_LINE_STRING\" FROM \"GEOM_ENTITY_WKT\" geom_entity_wkt_ WHERE (SDO_ANYINTERACT(geom_entity_wkt_.\"LOCATION\",SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?), 3857)) = 'TRUE')"
        Dialect.MYSQL      | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (ST_Intersects(geom_entity_json_.`location`,ST_GeomFromGeoJSON(?, 1, 3857)))'
        Dialect.MYSQL      | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (ST_Intersects(geom_entity_wkt_.`location`,ST_GeomFromText(?, 3857)))'
        Dialect.H2         | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (ST_Intersects(geom_entity_json_.`location`,ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)))'
        Dialect.H2         | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (ST_Intersects(geom_entity_wkt_.`location`,ST_GeomFromText(?, 3857)))'
        Dialect.POSTGRES   | GeomEntityJson || 'SELECT geom_entity_json_."id",ST_AsGeoJSON(geom_entity_json_."location") AS "location",ST_AsGeoJSON(geom_entity_json_."multi_point") AS "multi_point",ST_AsGeoJSON(geom_entity_json_."line_string") AS "line_string",ST_AsGeoJSON(geom_entity_json_."multi_line_string") AS "multi_line_string" FROM "geom_entity_json" geom_entity_json_ WHERE (ST_Intersects(geom_entity_json_."location",ST_SetSRID(ST_GeomFromGeoJSON(?), 3857)))'
        Dialect.POSTGRES   | GeomEntityWkt  || 'SELECT geom_entity_wkt_."id",ST_AsText(geom_entity_wkt_."location") AS "location",ST_AsText(geom_entity_wkt_."multi_point") AS "multi_point",ST_AsText(geom_entity_wkt_."line_string") AS "line_string",ST_AsText(geom_entity_wkt_."multi_line_string") AS "multi_line_string" FROM "geom_entity_wkt" geom_entity_wkt_ WHERE (ST_Intersects(geom_entity_wkt_."location",ST_GeomFromText(?, 3857)))'
        Dialect.SQL_SERVER | GeomEntityJson || 'SELECT geom_entity_json_.[id],geom_entity_json_.[location].STAsText() AS [location],geom_entity_json_.[multi_point].STAsText() AS [multi_point],geom_entity_json_.[line_string].STAsText() AS [line_string],geom_entity_json_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_json] geom_entity_json_ WHERE (geom_entity_json_.[location].STIntersects(geometry::STGeomFromText(?, 3857)) = 1)'
        Dialect.SQL_SERVER | GeomEntityWkt  || 'SELECT geom_entity_wkt_.[id],geom_entity_wkt_.[location].STAsText() AS [location],geom_entity_wkt_.[multi_point].STAsText() AS [multi_point],geom_entity_wkt_.[line_string].STAsText() AS [line_string],geom_entity_wkt_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_wkt] geom_entity_wkt_ WHERE (geom_entity_wkt_.[location].STIntersects(geometry::STGeomFromText(?, 3857)) = 1)'
    }

    @Unroll
    void "test encode #dialect near predicate for #entityClass.simpleName"() {
        given:
        def query = builder.createQuery(entityClass)
        def root = query.from(entityClass)
        query.where(builder.near(root.get('point'), builder.parameter(Object), builder.parameter(Double)))

        expect:
        query.build(new SqlQueryBuilder(dialect)).query == expectedQuery

        where:
        dialect            | entityClass    || expectedQuery
        Dialect.ORACLE     | GeomEntityJson || "SELECT geom_entity_json_.\"ID\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"LOCATION\") AS \"LOCATION\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"MULTI_POINT\") AS \"MULTI_POINT\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"LINE_STRING\") AS \"LINE_STRING\",SDO_UTIL.TO_GEOJSON(geom_entity_json_.\"MULTI_LINE_STRING\") AS \"MULTI_LINE_STRING\" FROM \"GEOM_ENTITY_JSON\" geom_entity_json_ WHERE (SDO_WITHIN_DISTANCE(geom_entity_json_.\"LOCATION\",SDO_UTIL.FROM_GEOJSON(?, NULL, 3857),'distance=' || ?) = 'TRUE')"
        Dialect.ORACLE     | GeomEntityWkt  || "SELECT geom_entity_wkt_.\"ID\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"LOCATION\") AS \"LOCATION\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"MULTI_POINT\") AS \"MULTI_POINT\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"LINE_STRING\") AS \"LINE_STRING\",SDO_UTIL.TO_WKTGEOMETRY(geom_entity_wkt_.\"MULTI_LINE_STRING\") AS \"MULTI_LINE_STRING\" FROM \"GEOM_ENTITY_WKT\" geom_entity_wkt_ WHERE (SDO_WITHIN_DISTANCE(geom_entity_wkt_.\"LOCATION\",SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(?), 3857),'distance=' || ?) = 'TRUE')"
        Dialect.MYSQL      | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (ST_Distance(geom_entity_json_.`location`,ST_GeomFromGeoJSON(?, 1, 3857)) <= ?)'
        Dialect.MYSQL      | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (ST_Distance(geom_entity_wkt_.`location`,ST_GeomFromText(?, 3857)) <= ?)'
        Dialect.H2         | GeomEntityJson || 'SELECT geom_entity_json_.`id`,ST_AsGeoJSON(geom_entity_json_.`location`) AS `location`,ST_AsGeoJSON(geom_entity_json_.`multi_point`) AS `multi_point`,ST_AsGeoJSON(geom_entity_json_.`line_string`) AS `line_string`,ST_AsGeoJSON(geom_entity_json_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_json` geom_entity_json_ WHERE (ST_DWithin(geom_entity_json_.`location`,ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),?))'
        Dialect.H2         | GeomEntityWkt  || 'SELECT geom_entity_wkt_.`id`,ST_AsText(geom_entity_wkt_.`location`) AS `location`,ST_AsText(geom_entity_wkt_.`multi_point`) AS `multi_point`,ST_AsText(geom_entity_wkt_.`line_string`) AS `line_string`,ST_AsText(geom_entity_wkt_.`multi_line_string`) AS `multi_line_string` FROM `geom_entity_wkt` geom_entity_wkt_ WHERE (ST_DWithin(geom_entity_wkt_.`location`,ST_GeomFromText(?, 3857),?))'
        Dialect.POSTGRES   | GeomEntityJson || 'SELECT geom_entity_json_."id",ST_AsGeoJSON(geom_entity_json_."location") AS "location",ST_AsGeoJSON(geom_entity_json_."multi_point") AS "multi_point",ST_AsGeoJSON(geom_entity_json_."line_string") AS "line_string",ST_AsGeoJSON(geom_entity_json_."multi_line_string") AS "multi_line_string" FROM "geom_entity_json" geom_entity_json_ WHERE (ST_DWithin(geom_entity_json_."location",ST_SetSRID(ST_GeomFromGeoJSON(?), 3857),?))'
        Dialect.POSTGRES   | GeomEntityWkt  || 'SELECT geom_entity_wkt_."id",ST_AsText(geom_entity_wkt_."location") AS "location",ST_AsText(geom_entity_wkt_."multi_point") AS "multi_point",ST_AsText(geom_entity_wkt_."line_string") AS "line_string",ST_AsText(geom_entity_wkt_."multi_line_string") AS "multi_line_string" FROM "geom_entity_wkt" geom_entity_wkt_ WHERE (ST_DWithin(geom_entity_wkt_."location",ST_GeomFromText(?, 3857),?))'
        Dialect.SQL_SERVER | GeomEntityJson || 'SELECT geom_entity_json_.[id],geom_entity_json_.[location].STAsText() AS [location],geom_entity_json_.[multi_point].STAsText() AS [multi_point],geom_entity_json_.[line_string].STAsText() AS [line_string],geom_entity_json_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_json] geom_entity_json_ WHERE (geom_entity_json_.[location].STDistance(geometry::STGeomFromText(?, 3857)) <= ?)'
        Dialect.SQL_SERVER | GeomEntityWkt  || 'SELECT geom_entity_wkt_.[id],geom_entity_wkt_.[location].STAsText() AS [location],geom_entity_wkt_.[multi_point].STAsText() AS [multi_point],geom_entity_wkt_.[line_string].STAsText() AS [line_string],geom_entity_wkt_.[multi_line_string].STAsText() AS [multi_line_string] FROM [geom_entity_wkt] geom_entity_wkt_ WHERE (geom_entity_wkt_.[location].STDistance(geometry::STGeomFromText(?, 3857)) <= ?)'
    }

    @Unroll
    void "test encode #dialect near predicate with geographic crs for #entityClass.simpleName"() {
        given:
        def query = builder.createQuery(entityClass)
        def root = query.from(entityClass)
        query.where(builder.near(root.get('point'), builder.parameter(Object), builder.parameter(Double)))

        expect:
        query.build(new SqlQueryBuilder(dialect)).query == expectedQuery

        where:
        dialect       | entityClass        || expectedQuery
        Dialect.MYSQL | GeomEntityWGS84    || 'SELECT geom_entity_wgs84_.`id`,ST_AsGeoJSON(geom_entity_wgs84_.`location`) AS `location` FROM `geom_entity_wgs84` geom_entity_wgs84_ WHERE (ST_Distance_Sphere(geom_entity_wgs84_.`location`,ST_GeomFromGeoJSON(?, 1, 4326)) <= ?)'
        Dialect.MYSQL | GeomEntityWGS84Wkt || 'SELECT geom_entity_wgs84_wkt_.`id`,ST_AsText(geom_entity_wgs84_wkt_.`location`) AS `location` FROM `geom_entity_wgs84_wkt` geom_entity_wgs84_wkt_ WHERE (ST_Distance_Sphere(geom_entity_wgs84_wkt_.`location`,ST_GeomFromText(?, 4326)) <= ?)'
        Dialect.H2    | GeomEntityWGS84    || 'SELECT geom_entity_wgs84_.`id`,ST_AsGeoJSON(geom_entity_wgs84_.`location`) AS `location` FROM `geom_entity_wgs84` geom_entity_wgs84_ WHERE (ST_DistanceSphere(geom_entity_wgs84_.`location`,ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)) <= ?)'
        Dialect.H2    | GeomEntityWGS84Wkt || 'SELECT geom_entity_wgs84_wkt_.`id`,ST_AsText(geom_entity_wgs84_wkt_.`location`) AS `location` FROM `geom_entity_wgs84_wkt` geom_entity_wgs84_wkt_ WHERE (ST_DistanceSphere(geom_entity_wgs84_wkt_.`location`,ST_GeomFromText(?, 4326)) <= ?)'
    }

    void "test encode create statement for embedded"() {
        given:
        PersistentEntity entity = new RuntimePersistentEntity(Restaurant)
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def result = encoder.buildBatchCreateTableStatement(entity)

        expect:
        result == 'CREATE TABLE `restaurant` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`name` VARCHAR(255) NOT NULL,`street` VARCHAR(255) NOT NULL,`zip_code` VARCHAR(255) NOT NULL,`hqaddress_street` VARCHAR(255),`hqaddress_zip_code` VARCHAR(255));'
    }

    void "test encode insert statement - custom mapping strategy"() {
        given:
        def result = builder.createCriteriaInsert(CountryRegion).build(new SqlQueryBuilder())

        expect:
        result.query == 'INSERT INTO "CountryRegion" ("name","countryId") VALUES (?,?)'
    }

    void "test encode insert statement - custom mapping"() {
        given:
        def result = builder.createCriteriaInsert(City).build(new SqlQueryBuilder())

        expect:
        result.query == 'INSERT INTO "T_CITY" ("C_NAME","country_region_id") VALUES (?,?)'
    }


    void "test encode insert statement - assigned id"() {
        given:
        def result = builder.createCriteriaInsert(PersonAssignedId).build(new SqlQueryBuilder())

        expect:
        result.query == 'INSERT INTO "person_assigned_id" ("name","age","enabled","id") VALUES (?,?,?,?)'
        result.parameters.equals('1':'name', '2': 'age', '3': 'enabled', '4': 'id')
    }

    @Unroll
    void "test build insert embedded"() {
        when:
            QueryResult encodedQuery = builder.createCriteriaInsert(type).build(new SqlQueryBuilder())

        then:
            encodedQuery.query == query

        where:
            type << [
                    Shipment,
                    UuidEntity,
                    UserRole
            ]
            query << [
                    'INSERT INTO "Shipment1" ("field","sp_country","sp_city") VALUES (?,?,?)',
                    'INSERT INTO "uuid_entity" ("name","child_id","xyz","embedded_child2_id","nullable_value","uuid") VALUES (?,?,?,?,?,?)',
                    'INSERT INTO "user_role_composite" ("user_id","role_id") VALUES (?,?)'
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
                    'CREATE TABLE "uuid_entity" ("uuid" UUID,"name" VARCHAR(255) NOT NULL,"child_id" UUID,"xyz" UUID,"embedded_child2_id" UUID,"nullable_value" UUID, PRIMARY KEY("uuid"));',
                    'CREATE TABLE "user_role_composite" ("user_id" BIGINT NOT NULL,"role_id" BIGINT NOT NULL, PRIMARY KEY("user_id","role_id"));'
            ]
    }

    void "test build create index from embedded class and field annotations"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Vehicle))

        then:
        statements[0] == 'CREATE TABLE `vehicle` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`name` VARCHAR(255) NOT NULL,`plate_number` VARCHAR(255) NOT NULL,`status` VARCHAR(255) NOT NULL,`jurisdiction_country_code` VARCHAR(255) NOT NULL,`jurisdiction_region_code` VARCHAR(255) NOT NULL,`second_plate_number` VARCHAR(255) NOT NULL,`second_status` VARCHAR(255) NOT NULL,`second_jurisdiction_country_code` VARCHAR(255) NOT NULL,`second_jurisdiction_region_code` VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE INDEX `idx_vehicle_name` ON `vehicle` (`name`);'
        statements[2] == 'CREATE INDEX `idx_vehicle_plate_number` ON `vehicle` (`plate_number`);'
        statements[3] == 'CREATE INDEX `idx_vehicle_status` ON `vehicle` (`status`);'
        statements[4] == 'CREATE INDEX `idx_vehicle_jurisdiction_region_code` ON `vehicle` (`jurisdiction_region_code`);'
        statements[5] == 'CREATE INDEX `idx_vehicle_second_plate_number` ON `vehicle` (`second_plate_number`);'
        statements[6] == 'CREATE INDEX `idx_vehicle_second_status` ON `vehicle` (`second_status`);'
        statements[7] == 'CREATE INDEX `idx_vehicle_second_jurisdiction_region_code` ON `vehicle` (`second_jurisdiction_region_code`);'
        statements.toSet().size() == statements.size()
    }

    void "test build create index from table annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndex))

        then:
        statements[0] == 'CREATE TABLE `shipment_with_index` (`shipment_id` BIGINT PRIMARY KEY AUTO_INCREMENT,`field` VARCHAR(255) NOT NULL,`taxCode` VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX `idx_shipment_with_index_field_taxcode` ON `shipment_with_index` (`field`, `taxCode`);'

        when:
        def productStatements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Product), List.of())

        then:
        productStatements.length == 1
        productStatements[0] == 'CREATE TABLE `product` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`name` VARCHAR(255) NOT NULL,`price` DECIMAL NOT NULL,`loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name` VARCHAR(255),`date_created` TIMESTAMP(6) DEFAULT NOW(6),`last_updated` TIMESTAMP(6) DEFAULT NOW(6),`category_id` BIGINT);'
    }

    void "test build create index from field annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnFields))

        then:
        statements[0] == 'CREATE TABLE `shipment_with_index_on_fields` (`shipment_id` BIGINT PRIMARY KEY AUTO_INCREMENT,`field` VARCHAR(255) NOT NULL,`taxCode` VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX `idx_shipment_with_index_on_fields_field` ON `shipment_with_index_on_fields` (`field`);'
        statements[2] == 'CREATE INDEX `idx_shipment_with_index_on_fields_taxcode` ON `shipment_with_index_on_fields` (`taxCode`);'
    }

    void "test build create index from field annotation with composite indexes"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnFieldsCompositeIndexes))

        then:
        statements[0] == 'CREATE TABLE `shipment_with_index_on_fields_composite_indexes` (`shipment_id` BIGINT PRIMARY KEY AUTO_INCREMENT,`field` VARCHAR(255) NOT NULL,`taxCode` VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX `idx_shipment_with_index_on_fields_composite_indexes_field_taxcode` ON `shipment_with_index_on_fields_composite_indexes` (`field`, `taxCode`);'
    }

    void "test build create index from index class annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnClass))

        then:
        statements[0] == 'CREATE TABLE `shipment_with_index_on_class` (`shipment_id` BIGINT PRIMARY KEY AUTO_INCREMENT,`field` VARCHAR(255) NOT NULL,`taxCode` VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX `idx_shipment_with_index_on_class_field` ON `shipment_with_index_on_class` (`field`);'
        statements[2] == 'CREATE INDEX `idx_shipment_tax` ON `shipment_with_index_on_class` (`taxCode`);'
    }

    void "test build create index from index class annotation and field annotation"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.MYSQL)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(ShipmentWithIndexOnClassAndFields))

        then:
        statements[0] == 'CREATE TABLE `shipment_with_index_on_class_and_fields` (`shipment_id` BIGINT PRIMARY KEY AUTO_INCREMENT,`field2` VARCHAR(255) NOT NULL,`taxCode2` VARCHAR(255) NOT NULL,`field` VARCHAR(255) NOT NULL,`taxCode` VARCHAR(255) NOT NULL);'
        statements[1] == 'CREATE UNIQUE INDEX `idx_shipment_with_index_on_class_and_fields_field` ON `shipment_with_index_on_class_and_fields` (`field`);'
        statements[2] == 'CREATE INDEX `idx_shipment_with_index_on_class_and_fields_taxcode` ON `shipment_with_index_on_class_and_fields` (`taxCode`);'
        statements[3] == 'CREATE UNIQUE INDEX `idx_shipment_with_index_on_class_and_fields_field2_taxcode2` ON `shipment_with_index_on_class_and_fields` (`field2`, `taxCode2`);'
    }

    @Unroll
    void "test build create index for geometry columns on #dialect"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(dialect)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(GeomEntityJson))

        then:
        statements.toList() == expectedStatements

        where:
        dialect            | expectedStatements
        Dialect.MYSQL      | [
                'CREATE TABLE `geom_entity_json` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`location` GEOMETRY NOT NULL,`multi_point` GEOMETRY NOT NULL,`line_string` GEOMETRY NOT NULL,`multi_line_string` GEOMETRY);',
                'CREATE SPATIAL INDEX `idx_geom_entity_json_location` ON `geom_entity_json` (`location`);',
                'CREATE SPATIAL INDEX `idx_geom_entity_json_multi_point` ON `geom_entity_json` (`multi_point`);'
        ]
        Dialect.POSTGRES   | [
                'CREATE TABLE "geom_entity_json" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"location" GEOMETRY NOT NULL,"multi_point" GEOMETRY NOT NULL,"line_string" GEOMETRY NOT NULL,"multi_line_string" GEOMETRY);',
                'CREATE INDEX "idx_geom_entity_json_location" ON "geom_entity_json" USING GIST ("location");',
                'CREATE INDEX "idx_geom_entity_json_multi_point" ON "geom_entity_json" USING GIST ("multi_point");'
        ]
        Dialect.H2         | [
                'CREATE TABLE `geom_entity_json` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`location` GEOMETRY NOT NULL,`multi_point` GEOMETRY NOT NULL,`line_string` GEOMETRY NOT NULL,`multi_line_string` GEOMETRY);',
                'CREATE SPATIAL INDEX `idx_geom_entity_json_location` ON `geom_entity_json` (`location`);',
                'CREATE SPATIAL INDEX `idx_geom_entity_json_multi_point` ON `geom_entity_json` (`multi_point`);'
        ]
        Dialect.SQL_SERVER | [
                'CREATE TABLE [geom_entity_json] ([id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL,[location] GEOMETRY NOT NULL,[multi_point] GEOMETRY NOT NULL,[line_string] GEOMETRY NOT NULL,[multi_line_string] GEOMETRY);',
                'CREATE SPATIAL INDEX [idx_geom_entity_json_location] ON [geom_entity_json] ([location]) USING GEOMETRY_GRID WITH (BOUNDING_BOX = (-20037508.3427892, -20037508.3427892, 20037508.3427892,  20037508.3427892));',
                'CREATE SPATIAL INDEX [idx_geom_entity_json_multi_point] ON [geom_entity_json] ([multi_point]);'
        ]
    }

    @Unroll
    void "test build create index for geography columns on #dialect"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(dialect)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(GeogEntityJson))

        then:
        statements.toList() == expectedStatements

        where:
        dialect            | expectedStatements
        Dialect.POSTGRES   | [
                'CREATE TABLE "geog_entity_json" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"location" GEOGRAPHY NOT NULL,"multi_point" GEOGRAPHY NOT NULL,"line_string" GEOGRAPHY NOT NULL,"multi_line_string" GEOGRAPHY);',
                'CREATE INDEX "idx_geog_entity_json_location" ON "geog_entity_json" USING GIST ("location");',
                'CREATE INDEX "idx_geog_entity_json_multi_point" ON "geog_entity_json" USING GIST ("multi_point");'
        ]
        Dialect.SQL_SERVER | [
                'CREATE TABLE [geog_entity_json] ([id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL,[location] GEOGRAPHY NOT NULL,[multi_point] GEOGRAPHY NOT NULL,[line_string] GEOGRAPHY NOT NULL,[multi_line_string] GEOGRAPHY);',
                'CREATE SPATIAL INDEX [idx_geog_entity_json_location] ON [geog_entity_json] ([location]);',
                'CREATE SPATIAL INDEX [idx_geog_entity_json_multi_point] ON [geog_entity_json] ([multi_point]);'
        ]
    }

    void "test build create index for geometry entity columns on oracle"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.ORACLE)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(GeomEntityJson))

        then:
        statements[0] == 'CREATE TABLE "GEOM_ENTITY_JSON" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"LOCATION" SDO_GEOMETRY NOT NULL,"MULTI_POINT" SDO_GEOMETRY NOT NULL,"LINE_STRING" SDO_GEOMETRY NOT NULL,"MULTI_LINE_STRING" SDO_GEOMETRY)'
        statements[1] == 'CREATE SEQUENCE "GEOM_ENTITY_JSON_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE'
        statements[2].contains("INSERT INTO USER_SDO_GEOM_METADATA")
        statements[2].contains("'location'")
        statements[2].contains("3857")
        statements[3].contains("INSERT INTO USER_SDO_GEOM_METADATA")
        statements[3].contains("'multi_point'")
        statements[3].contains("4326")
        statements[4].contains("INSERT INTO USER_SDO_GEOM_METADATA")
        statements[4].contains("'line_string'")
        statements[4].contains("3857")
        statements[5] == 'CREATE INDEX "IDX_GEOM_ENTITY_JSON_LOCATION" ON "GEOM_ENTITY_JSON" ("LOCATION") INDEXTYPE IS MDSYS.SPATIAL_INDEX'
        statements[6] == 'CREATE INDEX "IDX_GEOM_ENTITY_JSON_MULTI_POINT" ON "GEOM_ENTITY_JSON" ("MULTI_POINT") INDEXTYPE IS MDSYS.SPATIAL_INDEX'
    }

    void "test build create index for embedded geometry entity columns on oracle"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.ORACLE)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(School))

        then:
        statements[0] == 'CREATE TABLE "SCHOOL" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"NAME" VARCHAR(255) NOT NULL,"POINT" SDO_GEOMETRY NOT NULL,"DESCRIPTION" VARCHAR(255))'
        statements[1] == 'CREATE SEQUENCE "SCHOOL_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE'
        statements[2].contains("INSERT INTO USER_SDO_GEOM_METADATA")
        statements[3] == 'CREATE INDEX "IDX_SCHOOL_POINT" ON "SCHOOL" ("POINT") INDEXTYPE IS MDSYS.SPATIAL_INDEX'
    }

    @Unroll
    void "test build create index for embedded geometry entity columns on #dialect"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(dialect)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(School))

        then:
        statements[0] == tableStatement
        statements[1] == indexStatement

        where:
        dialect            | tableStatement                                                                                                                                                    | indexStatement
        Dialect.MYSQL      | 'CREATE TABLE `school` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`name` VARCHAR(255) NOT NULL,`point` GEOMETRY NOT NULL,`description` VARCHAR(255));'               | 'CREATE SPATIAL INDEX `idx_school_point` ON `school` (`point`);'
        Dialect.POSTGRES   | 'CREATE TABLE "school" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"name" VARCHAR(255) NOT NULL,"point" GEOMETRY NOT NULL,"description" VARCHAR(255));' | 'CREATE INDEX "idx_school_point" ON "school" USING GIST ("point");'
        Dialect.H2         | 'CREATE TABLE `school` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`name` VARCHAR(255) NOT NULL,`point` GEOMETRY NOT NULL,`description` VARCHAR(255));' | 'CREATE SPATIAL INDEX `idx_school_point` ON `school` (`point`);'
        Dialect.SQL_SERVER | 'CREATE TABLE [school] ([id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL,[name] VARCHAR(255) NOT NULL,[point] GEOMETRY NOT NULL,[description] VARCHAR(255));'       | 'CREATE SPATIAL INDEX [idx_school_point] ON [school] ([point]);'
    }

    void "test build create index for SQL Server geometry column 4326 srid"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.SQL_SERVER)
        def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(GeomEntityWGS84))

        then:
        statements[0] == 'CREATE TABLE [geom_entity_wgs84] ([id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL,[location] GEOMETRY NOT NULL);'
        statements[1] == 'CREATE SPATIAL INDEX [idx_geom_entity_wgs84_location] ON [geom_entity_wgs84] ([location]) USING GEOMETRY_GRID WITH (BOUNDING_BOX = (-180, -90, 180,  90));'
    }

    void "test build create table rejects composite index containing geometry column"() {
        when:
        QueryBuilder encoder = new SqlQueryBuilder(Dialect.SQL_SERVER)
        encoder.buildCreateTableStatements(getRuntimePersistentEntity(GeomEntityCompositeIndex))

        then:
        def e = thrown(MappingException)
        e.message == 'A geospatial column cannot be included in a composite index. Index columns: [point, name_col]'
    }

    void "test build composite id query"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def query = builder.createQuery()
            def root = query.from(Project)
            def q = query.where(builder.equal(root.id(), builder.parameter(Object))).build(encoder)

        then:
            q.query == 'SELECT project_."department_id",project_."project_id",LOWER(project_.name) AS name,project_.name AS db_name,UPPER(project_.org) AS org FROM "project" project_ WHERE (project_."department_id" = ? AND project_."project_id" = ?)'
            q.parameters == [
                    '1': 'projectId.departmentId',
                    '2': 'projectId.projectId'
            ]
    }

    void "test insert statement with version"() {
        when:
            def insertResult = builder.createCriteriaInsert(Bike).build(new SqlQueryBuilder())

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
