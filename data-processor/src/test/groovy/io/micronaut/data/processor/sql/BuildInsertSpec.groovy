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
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.PendingFeature
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getDataTypes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery
import static io.micronaut.data.processor.visitors.TestUtils.getRawQuery

class BuildInsertSpec extends AbstractDataSpec {

    @Unroll
    void "test build create table for identity generation and dialect - #dialect"() {
        given:
        ClassElement element = buildClassElement("""
package test;
import io.micronaut.data.annotation.*;

class Test {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;

    public Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

""")
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def entity = new SourcePersistentEntity(element, {})
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == query

        where:
        dialect            | query
        Dialect.ORACLE     | 'CREATE TABLE "TEST" ("ID" NUMBER(19) GENERATED ALWAYS AS IDENTITY (MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE) PRIMARY KEY,"NAME" VARCHAR(255) NOT NULL)'
        Dialect.H2         | 'CREATE TABLE `test` (`id` BIGINT AUTO_INCREMENT PRIMARY KEY,`name` VARCHAR(255) NOT NULL);'
        Dialect.POSTGRES   | 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"name" VARCHAR(255) NOT NULL);'
        Dialect.SQL_SERVER | 'CREATE TABLE [test] ([id] BIGINT PRIMARY KEY IDENTITY(1,1) NOT NULL,[name] VARCHAR(255) NOT NULL);'
        Dialect.MYSQL      | 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`name` VARCHAR(255) NOT NULL);'
    }

    @Unroll
    void "test build insert for identity generation and dialect - #dialect"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.MyInterface', """
import java.util.UUID;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect=Dialect.${dialect.name()})
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Test, Long> {
    Test save(String name);

    Test findById(Long id);
}

@MappedEntity
class Test {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;

    public Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

""")
        expect:
        getQuery(beanDefinition.getRequiredMethod("save", String)) == query

        where:
        dialect            | query
        Dialect.MYSQL      | 'INSERT INTO `test` (`name`) VALUES (?)'
        Dialect.ORACLE     | 'INSERT INTO "TEST" ("NAME") VALUES (?)'
        Dialect.H2         | 'INSERT INTO `test` (`name`) VALUES (?)'
        Dialect.POSTGRES   | 'INSERT INTO "test" ("name") VALUES (?)'
        Dialect.SQL_SERVER | 'INSERT INTO [test] ([name]) VALUES (?)'
    }

    @Unroll
    void "test build create table for UUID and dialect - #dialect"() {
        given:
        ClassElement element = buildClassElement("""
package test;
import java.util.UUID;
import io.micronaut.data.annotation.*;

class Test {
    @Id
    @GeneratedValue
    private UUID id;
    private String name;

    public Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}

""")
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def entity = new SourcePersistentEntity(element, {})
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == query

        where:
        dialect            | query
        Dialect.ORACLE     | 'CREATE TABLE "TEST" ("ID" VARCHAR(36) NOT NULL DEFAULT SYS_GUID() PRIMARY KEY,"NAME" VARCHAR(255) NOT NULL)'
        Dialect.H2         | 'CREATE TABLE `test` (`id` UUID NOT NULL DEFAULT random_uuid() PRIMARY KEY,`name` VARCHAR(255) NOT NULL);'
        Dialect.POSTGRES   | 'CREATE TABLE "test" ("id" UUID PRIMARY KEY NOT NULL DEFAULT uuid_generate_v4(),"name" VARCHAR(255) NOT NULL);'
        Dialect.SQL_SERVER | 'CREATE TABLE [test] ([id] UNIQUEIDENTIFIER PRIMARY KEY NOT NULL DEFAULT newid(),[name] VARCHAR(255) NOT NULL);'
        Dialect.MYSQL      | 'CREATE TABLE `test` (`id` VARCHAR(36) PRIMARY KEY NOT NULL,`name` VARCHAR(255) NOT NULL);'
    }

    @Unroll
    void "test build insert for UUID and dialect - #dialect"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.MyInterface', """
import java.util.UUID;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect=Dialect.${dialect.name()})
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Test, UUID> {
    Test save(String name);

    Test findById(UUID id);
}

@MappedEntity
class Test {
    @Id
    @GeneratedValue
    private UUID id;
    private String name;

    public Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}

""")
        expect:
        getQuery(beanDefinition.getRequiredMethod("save", String)) == query
        getDataTypes(beanDefinition.getRequiredMethod("findById", UUID)) == [DataType.UUID]

        where:
        dialect            | query
        Dialect.MYSQL      | 'INSERT INTO `test` (`name`,`id`) VALUES (?,?)'
        Dialect.ORACLE     | 'INSERT INTO "TEST" ("NAME") VALUES (?)'
        Dialect.H2         | 'INSERT INTO `test` (`name`) VALUES (?)'
        Dialect.POSTGRES   | 'INSERT INTO "test" ("name") VALUES (?)'
        Dialect.SQL_SERVER | 'INSERT INTO [test] ([name]) VALUES (?)'
    }

    void "test build SQL insert statement with custom name and escaping"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.DataType;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false)
@io.micronaut.context.annotation.Executable
interface MyInterface extends CrudRepository<TableRatings, Long> {
}

@MappedEntity(value = "T-Table-Ratings", escape = true)
class TableRatings {
    @Id
    @GeneratedValue
    private Long id;

    @MappedProperty(value = "T-Rating", type = DataType.INTEGER)
    private final int rating;

    public TableRatings(int rating) {
        this.rating = rating;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getRating() {
        return rating;
    }
}

""")

        def method = beanDefinition.findPossibleMethods("save").findFirst().get()

        expect:
        getQuery(method) == 'INSERT INTO "T-Table-Ratings" ("T-Rating") VALUES (?)'
    }

    void "test build SQL insert statement"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false)
@io.micronaut.context.annotation.Executable
interface MyInterface extends CrudRepository<Person, Long> {
}
""")

        def method = beanDefinition.getRequiredMethod("save", Person)

        expect:
        getQuery(method) == 'INSERT INTO "person" ("name","age","enabled","public_id","company_id") VALUES (?,?,?,?,?)'
        getParameterPropertyPaths(method) == ['name', 'age', 'enabled', 'publicId', 'company.myId'] as String[]
    }

    @PendingFeature(reason = "Bug in Micronaut core. Fixed by https://github.com/micronaut-projects/micronaut-core/commit/f6a488677d587be309d5b0abd8925c9a098cfdf9")
    void "test build SQL insert statement for repo with no super interface"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.TestBookPageRepository' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.tck.entities.Shelf;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.ShelfBook;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false)
interface TestBookPageRepository extends io.micronaut.data.tck.repositories.BookPageRepository {

}
""")

        def method = beanDefinition.findPossibleMethods("save").findFirst().get()
        expect:

        getQuery(method) == 'INSERT INTO "shelf_book" ("shelf_id","book_id") VALUES (?,?)'
        getParameterPropertyPaths(method) == ['name', 'age', 'enabled', 'publicId'] as String[]
    }

    void "test build SQL update"() {
        given:
            BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.tck.entities.Food;
import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import java.util.UUID;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false)
@io.micronaut.context.annotation.Executable
interface MyInterface extends CrudRepository<Food, UUID> {
}
""")

        def method = beanDefinition.findPossibleMethods("update").findFirst().get()
        expect:

        getQuery(method) == 'UPDATE "food" SET "key"=?,"carbohydrates"=?,"portion_grams"=?,"updated_on"=?,"fk_meal_id"=?,"fk_alt_meal"=?,"loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name"=?,"fresh"=? WHERE ("fid" = ? AND (fresh = \'Y\'))'
        getParameterPropertyPaths(method) == ["key", "carbohydrates", "portionGrams", "updatedOn", "meal.mid", "alternativeMeal.mid", "longName", "fresh", "fid"] as String[]
    }

    void "test build custom SQL insert"() {
        given:
            BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.tck.entities.Food;
import io.micronaut.data.tck.entities.Meal;
import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import java.util.UUID;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false)
@io.micronaut.context.annotation.Executable
interface MyInterface extends CrudRepository<Food, UUID> {

    @Query("INSERT INTO food(key, carbohydrates) VALUES (:key, :carbohydrates)")
    void saveCustom(java.util.List<Food> food);

    @Query("INSERT INTO food(key, carbohydrates) VALUES (:key, :carbohydrates)")
    void saveCustomSingle(Food food);

    Food saveCustom2(UUID fid, String key, int carbohydrates, Meal meal);

}
""")
        when:
        def saveCustom = beanDefinition.findPossibleMethods("saveCustom").findFirst().get()
        then:
        getRawQuery(saveCustom) == 'INSERT INTO food(key, carbohydrates) VALUES (?, ?)'
        getParameterPropertyPaths(saveCustom) == ["key", "carbohydrates"] as String[]
        when:
        def saveCustomSingle = beanDefinition.findPossibleMethods("saveCustomSingle").findFirst().get()
        then:
        getRawQuery(saveCustomSingle) == 'INSERT INTO food(key, carbohydrates) VALUES (?, ?)'
        getParameterPropertyPaths(saveCustomSingle) == ["key", "carbohydrates"] as String[]
        when:
        def save = beanDefinition.findPossibleMethods("saveCustom2").findFirst().get()
        then:
        getQuery(save) == 'INSERT INTO "food" ("key","carbohydrates","portion_grams","created_on","updated_on","fk_meal_id","fk_alt_meal","loooooooooooooooooooooooooooooooooooooooooooooooooooooooong_name","fresh","fid") VALUES (?,?,?,?,?,?,?,?,?,?)'
        getDataInterceptor(save) == "io.micronaut.data.intercept.SaveOneInterceptor"
    }
}
