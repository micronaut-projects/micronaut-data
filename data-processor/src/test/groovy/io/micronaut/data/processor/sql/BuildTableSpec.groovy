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

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Restaurant
import spock.lang.Unroll

//@Requires({ javaVersion <= 1.8 })
class BuildTableSpec extends AbstractDataSpec {


    void "test build create table table statement for nullable embeddable"() {
        given:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ANSI)
        def entity = PersistentEntity.of(Restaurant)
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:"@Nullable @Embedded doesn't include NOT NULL declaration"
        sql.contains("\"hqaddress_street\" VARCHAR(255),")

        and:"regular @Embedded does include NOT NULL declaration"
        sql.contains("\"address_street\" VARCHAR(255) NOT NULL,")
    }

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
        Dialect.MYSQL    | 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`json` JSON NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"json" JSONB NOT NULL);'
        Dialect.ORACLE   | 'CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE' + System.lineSeparator() +
                           'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"JSON" CLOB NOT NULL)'
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
        sql == 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"date_created" TIMESTAMP WITH TIME ZONE);'
    }

//    @PendingFeature(reason = "Waiting for https://github.com/micronaut-projects/micronaut-core/pull/4343")
    void "test custom parent entity with generics"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import java.time.LocalDateTime;

@Entity
class Test extends io.micronaut.data.tck.entities.BaseEntity<Long> {
}
''')

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"created_date" TIMESTAMP,"updated_date" TIMESTAMP);'
    }

    @Unroll
    void "test custom column restrictions #dialect"() {
        given:
            def entity = buildJpaEntity('test.Test', '''
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    @Column
    private String text1;
    @Column(length=10)
    private String text2;
    @jakarta.validation.constraints.Size(min=5, max=7)
    private String text3;

    private BigDecimal amount1;
    @Column(precision = 11, scale = 2)
    private BigDecimal amount2;
    @jakarta.validation.constraints.Size(min=5, max=7)
    private BigDecimal amount3;

    private Float floatAmount1;
    @Column(precision = 11, scale = 2)
    private Float floatAmount2;

    private Double doubleAmount1;
    @Column(precision = 11, scale = 2)
    private Double doubleAmount2;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText1() {
        return text1;
    }

    public void setText1(String text1) {
        this.text1 = text1;
    }

    public String getText2() {
        return text2;
    }

    public void setText2(String text2) {
        this.text2 = text2;
    }

    public String getText3() {
        return text3;
    }

    public void setText3(String text3) {
        this.text3 = text3;
    }

    public BigDecimal getAmount1() {
        return amount1;
    }

    public void setAmount1(BigDecimal amount1) {
        this.amount1 = amount1;
    }

    public BigDecimal getAmount2() {
        return amount2;
    }

    public void setAmount2(BigDecimal amount2) {
        this.amount2 = amount2;
    }

    public BigDecimal getAmount3() {
        return amount3;
    }

    public void setAmount3(BigDecimal amount3) {
        this.amount3 = amount3;
    }

    public Float getFloatAmount1() {
        return floatAmount1;
    }

    public void setFloatAmount1(Float floatAmount1) {
        this.floatAmount1 = floatAmount1;
    }

    public Float getFloatAmount2() {
        return floatAmount2;
    }

    public void setFloatAmount2(Float floatAmount2) {
        this.floatAmount2 = floatAmount2;
    }

    public Double getDoubleAmount1() {
        return doubleAmount1;
    }

    public void setDoubleAmount1(Double doubleAmount1) {
        this.doubleAmount1 = doubleAmount1;
    }

    public Double getDoubleAmount2() {
        return doubleAmount2;
    }

    public void setDoubleAmount2(Double doubleAmount2) {
        this.doubleAmount2 = doubleAmount2;
    }
}
''')

        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE `test` (`id` BIGINT AUTO_INCREMENT PRIMARY KEY,`text1` VARCHAR(255) NOT NULL,`text2` VARCHAR(10) NOT NULL,`text3` VARCHAR(7) NOT NULL,`amount1` DECIMAL NOT NULL,`amount2` NUMERIC(11,2) NOT NULL,`amount3` DECIMAL NOT NULL,`float_amount1` FLOAT NOT NULL,`float_amount2` NUMERIC(11,2) NOT NULL,`double_amount1` DOUBLE NOT NULL,`double_amount2` NUMERIC(11,2) NOT NULL);'
        Dialect.MYSQL    | 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`text1` VARCHAR(255) NOT NULL,`text2` VARCHAR(10) NOT NULL,`text3` VARCHAR(7) NOT NULL,`amount1` DECIMAL NOT NULL,`amount2` NUMERIC(11,2) NOT NULL,`amount3` DECIMAL NOT NULL,`float_amount1` FLOAT NOT NULL,`float_amount2` NUMERIC(11,2) NOT NULL,`double_amount1` DOUBLE NOT NULL,`double_amount2` NUMERIC(11,2) NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"text1" VARCHAR(255) NOT NULL,"text2" VARCHAR(10) NOT NULL,"text3" VARCHAR(7) NOT NULL,"amount1" DECIMAL NOT NULL,"amount2" NUMERIC(11,2) NOT NULL,"amount3" DECIMAL NOT NULL,"float_amount1" REAL NOT NULL,"float_amount2" NUMERIC(11,2) NOT NULL,"double_amount1" DOUBLE PRECISION NOT NULL,"double_amount2" NUMERIC(11,2) NOT NULL);'
        Dialect.ORACLE   | 'CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE' + System.lineSeparator() +
                'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"TEXT1" VARCHAR(255) NOT NULL,"TEXT2" VARCHAR(10) NOT NULL,"TEXT3" VARCHAR(7) NOT NULL,"AMOUNT1" FLOAT(126) NOT NULL,"AMOUNT2" NUMBER(11,2) NOT NULL,"AMOUNT3" FLOAT(126) NOT NULL,"FLOAT_AMOUNT1" FLOAT(53) NOT NULL,"FLOAT_AMOUNT2" NUMBER(11,2) NOT NULL,"DOUBLE_AMOUNT1" FLOAT(23) NOT NULL,"DOUBLE_AMOUNT2" NUMBER(11,2) NOT NULL)'
    }

    @Unroll
    void "test time datatype #dialect"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import io.micronaut.data.annotation.MappedProperty;
import java.sql.Time;

@Entity
class Test {
    @MappedProperty("wakeup_time")
    private Time wakeUpTime;

    public void setWakeUpTime(Time wakeUpTime) {
        this.wakeUpTime = wakeUpTime;
    }

    public Time getWakeUpTime() {
        return wakeUpTime;
    }}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE `test` (`wakeup_time` TIME(6)  NOT NULL );'
        Dialect.MYSQL    | 'CREATE TABLE `test` (`wakeup_time` TIME(6)  NOT NULL );'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("wakeup_time" TIME(6)  NOT NULL );'
        Dialect.ORACLE   | 'CREATE TABLE "TEST" ("WAKEUP_TIME" DATE  NOT NULL )'
    }

    void "test create table MappedProperty with Embedded"() {
        given:
        def entity = buildJpaEntity('test.EmbeddedEntity', '''
import io.micronaut.data.annotation.Embeddable;import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@MappedEntity
class EmbeddedEntity {
    @Id
    private Long id;

    @MappedProperty("emb_a_")
    @Relation(Relation.Kind.EMBEDDED)
    private Emb embA;

    @MappedProperty("emb_b_")
    @Relation(Relation.Kind.EMBEDDED)
    private Emb embB;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Emb getEmbA() {
        return this.embA;
    }

    public void setEmbA(Emb embA) {
        this.embA = embA;
    }

    public Emb getEmbB() {
        return this.embB;
    }

    public void setEmbB(Emb embB) {
        this.embB = embB;
    }
}
@Embeddable
class Emb {
    private String a;
    private String b;

    public String getA() {
        return  a;
    }
    public void setA(String a) {
        this.a = a;
    }
    public String getB() {
        return  b;
    }
    public void setB(String b) {
        this.b = b;
    }
}
''')

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE "embedded_entity" ("id" BIGINT NOT NULL,"emb_a_a" VARCHAR(255) NOT NULL,"emb_a_b" VARCHAR(255) NOT NULL,"emb_b_a" VARCHAR(255) NOT NULL,"emb_b_b" VARCHAR(255) NOT NULL);'
    }
}
