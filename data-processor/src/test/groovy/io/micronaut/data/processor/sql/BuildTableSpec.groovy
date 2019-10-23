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
package io.micronaut.data.processor.sql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Requires
import spock.lang.Unroll

@Requires({ javaVersion <= 1.8 })
class BuildTableSpec extends AbstractDataSpec {

    @Unroll
    void "test build create table for JSON type for dialect #dialect"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import java.util.Map;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    @io.micronaut.data.annotation.TypeDef(type=io.micronaut.data.model.DataType.JSON)
    private Map json;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Map getJson() {
        return json;
    }
    
    public void setJson(Map json) {
        this.json = json;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE `test` (`id` BIGINT AUTO_INCREMENT PRIMARY KEY,`json` JSON NOT NULL);'
        Dialect.MYSQL    | 'CREATE TABLE `test` (`id` BIGINT AUTO_INCREMENT PRIMARY KEY,`json` JSON NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("id" BIGINT GENERATED ALWAYS AS IDENTITY,"json" JSONB NOT NULL);'
        Dialect.ORACLE   | '''CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 NOCACHE NOCYCLE
CREATE TABLE "TEST" ("ID" NUMBER(19) PRIMARY KEY NOT NULL,"JSON" CLOB NOT NULL)'''
    }

    void "test custom column definition"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import java.time.LocalDateTime;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    @DateCreated
    private LocalDateTime dateCreated;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public LocalDateTime getDateCreated() {
        return dateCreated;
    }
    
    public void setDateCreated(LocalDateTime ldt) {
        dateCreated = ldt;
    }
}
''')

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE "test" ("id" BIGINT AUTO_INCREMENT PRIMARY KEY,"date_created" TIMESTAMP WITH TIME ZONE);'
    }
}
