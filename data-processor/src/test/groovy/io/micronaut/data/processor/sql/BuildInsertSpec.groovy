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


import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.PendingFeature
import spock.lang.Unroll

class BuildInsertSpec extends AbstractDataSpec {
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
        Dialect.ORACLE     | 'CREATE TABLE "TEST" ("ID" VARCHAR(36) PRIMARY KEY NOT NULL DEFAULT SYS_GUID(),"NAME" VARCHAR(255) NOT NULL)'
        Dialect.H2         | 'CREATE TABLE `test` (`id` UUID PRIMARY KEY NOT NULL DEFAULT random_uuid(),`name` VARCHAR(255) NOT NULL);'
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
        beanDefinition.getRequiredMethod("save", String)
                .stringValue(Query).get() == query
        beanDefinition.getRequiredMethod("findById", UUID)
                .stringValues(DataMethod, "parameterTypeDefs") == ['UUID'] as String[]

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
        method
                .stringValue(Query)
                .orElse(null) == 'INSERT INTO "T-Table-Ratings" ("T-Rating") VALUES (?)'
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
interface MyInterface extends CrudRepository<Person, Long> {
}
""")

        def method = beanDefinition.getRequiredMethod("save", Person)

        expect:
        method
                .stringValue(Query)
                .orElse(null) == 'INSERT INTO "person" ("name","age","enabled","public_id") VALUES (?,?,?,?)'
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING + "Paths") ==
                ['name', 'age', 'enabled', 'publicId'] as String[]
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

        expect:
        beanDefinition.findPossibleMethods("save")
                .findFirst().get()
                .stringValue(Query)
                .orElse(null) == 'INSERT INTO "shelf_book" ("shelf_id","book_id") VALUES (?,?)'
    }
}
